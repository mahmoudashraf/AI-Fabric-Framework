import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import AddRounded from '@mui/icons-material/AddRounded'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import CableRounded from '@mui/icons-material/CableRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import EditOutlined from '@mui/icons-material/EditOutlined'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import PublicRounded from '@mui/icons-material/PublicRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import UndoRounded from '@mui/icons-material/UndoRounded'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel, IconButton, Link, List, ListItemButton, ListItemText, Stack, Switch, Tooltip, Typography } from '@mui/material'
import { BrainPageHeader, brainPaneSx } from '../brain/BrainPageLayout'
import { mcpApi } from './api'
import { compareMcpTools, connectionEnabledInTarget, emptyConnection, existingToolPolicies, safeAuthorizationUrl, transportLabel } from './model'
import { McpAssistant } from './McpAssistant'
import { McpConnectionForm } from './McpConnectionForm'
import { McpToolPicker } from './McpToolPicker'
import { McpImportReview } from './McpImportReview'
import type { McpConnection, McpConnectionInput, McpDiscovery, McpImportInput, McpImportReceipt, McpUndoReceipt } from './types'

type Props = { brainId: string; targetProfileId: string; canManage: boolean; canImport: boolean; actionsConfig: unknown; href: (section?: string) => string }
type Command = { kind: 'save'; input: McpConnectionInput; original: McpConnection | null }
  | { kind: 'discover'; connection: McpConnection }
  | { kind: 'disconnect'; connection: McpConnection }
  | { kind: 'binding'; connection: McpConnection; enabled: boolean }
  | { kind: 'undo'; connection: McpConnection; receiptId: string }
  | { kind: 'import'; connection: McpConnection; input: McpImportInput }
const statusText = (value: string) => value ? value.replace(/_/g, ' ').toLowerCase() : 'Not checked'
const connected = (connection: McpConnection) => ['CONNECTED', 'AUTHORIZED', 'READY', 'ACTIVE'].includes(connection.authStatus)

export function McpConnections({ brainId, targetProfileId, canManage, canImport, actionsConfig, href }: Props) {
  const location = useLocation(), navigate = useNavigate(), client = useQueryClient()
  const params = new URLSearchParams(location.search)
  const selectedId = params.get('mcp') ?? ''
  const activeSelection = useRef(selectedId); activeSelection.current = selectedId
  const creating = selectedId === 'connect'
  const callback = params.get('mcpOAuth')
  const [editing, setEditing] = useState<McpConnection | null>(null)
  const [snapshot, setSnapshot] = useState<McpDiscovery | null>(null)
  const [receipt, setReceipt] = useState<McpImportReceipt | null>(null)
  const [disconnecting, setDisconnecting] = useState(false)
  const [bindingChange, setBindingChange] = useState<boolean | null>(null)
  const [notice, setNotice] = useState('')
  const [authError, setAuthError] = useState('')
  const [authorizing, setAuthorizing] = useState(false)
  const [authorization, setAuthorization] = useState<{ url: string; expiresAt: string; popup: Window | null } | null>(null)
  const [help, setHelp] = useState<string[] | null>(null)
  const live = useRef(true)
  const receiptElement = useRef<HTMLDivElement>(null)
  useEffect(() => { live.current = true; return () => { live.current = false } }, [])
  useEffect(() => { if (receipt) receiptElement.current?.scrollIntoView({ block: 'nearest' }) }, [receipt])
  const queryKey = ['mcp-connections', brainId, targetProfileId]
  const connections = useQuery({ queryKey, queryFn: () => mcpApi.list(brainId), enabled: Boolean(targetProfileId), retry: false, refetchOnWindowFocus: false })
  const selected = connections.data?.connections.find(connection => connection.id === selectedId)
  const savedSnapshot = useQuery({ queryKey: ['mcp-connection-snapshot', brainId, targetProfileId, selected?.id, selected?.lastDiscoveryId],
    queryFn: () => mcpApi.snapshot(brainId, targetProfileId, selected!.id, selected!.lastDiscoveryId!), enabled: Boolean(selected?.lastDiscoveryId && selected.status === 'ACTIVE'), retry: false, refetchOnWindowFocus: false })
  const listHref = (id = '') => {
    const next = new URLSearchParams(href('capabilities').split('?')[1])
    next.set('view', 'mcp')
    if (id) next.set('mcp', id)
    return `${href('capabilities').split('?')[0]}?${next}`
  }
  const refreshDraft = async () => {
    await Promise.all([client.invalidateQueries({ queryKey: ['deployment-draft', brainId] }), client.invalidateQueries({ queryKey: ['deployment-workspace', brainId] })])
  }
  const command = useMutation({ mutationKey: ['mcp-command', brainId, targetProfileId], retry: false,
    mutationFn: async (input: Command) => {
      if (input.kind === 'save') return input.original ? mcpApi.update(brainId, targetProfileId, input.original.id, input.original.revision, input.input, input.original.targetProfileId) : mcpApi.create(brainId, targetProfileId, input.input)
      if (input.kind === 'discover') return mcpApi.discover(brainId, targetProfileId, input.connection.id)
      if (input.kind === 'disconnect') return mcpApi.disconnect(brainId, targetProfileId, input.connection.id)
      if (input.kind === 'binding') return mcpApi.bindTarget(brainId, targetProfileId, input.connection.id, input.connection.revision, input.enabled)
      if (input.kind === 'undo') return mcpApi.undoImport(brainId, targetProfileId, input.connection.id, input.receiptId)
      return mcpApi.import(brainId, targetProfileId, input.connection.id, input.input)
    },
    onSuccess: async (result, input) => {
      await client.invalidateQueries({ queryKey: ['mcp-connections', brainId] })
      if (!live.current || activeSelection.current !== (input.kind === 'save' ? input.original?.id ?? 'connect' : input.connection.id)) return
      if (input.kind === 'save') {
        setEditing(null); setSnapshot(null); setReceipt(null); setNotice('Connection saved. Discover tools when you are ready.'); navigate(listHref((result as McpConnection).id), { replace: creating })
      } else if (input.kind === 'discover') {
        setSnapshot(result as McpDiscovery); setReceipt(null); setNotice('')
      } else if (input.kind === 'disconnect') {
        setSnapshot(null); setDisconnecting(false); setAuthorization(null); setNotice('Connection disconnected. Existing action definitions remain in your draft.')
      } else if (input.kind === 'binding') {
        setBindingChange(null); setNotice(input.enabled ? 'Connection enabled in this environment. Existing imported actions are reused; no credentials were copied and no release was published.' : 'Connection disabled in this environment. Other environment bindings and saved action definitions are unchanged.')
      } else if (input.kind === 'undo') {
        setReceipt(null); setNotice(`Import undone. Draft revision ${(result as McpUndoReceipt).draftRevision}. Nothing was published or applied.`); await refreshDraft()
      } else {
        setReceipt(result as McpImportReceipt); await refreshDraft()
      }
    },
  })
  useEffect(() => {
    setEditing(null); setSnapshot(null); setReceipt(null); setDisconnecting(false); setBindingChange(null); setNotice(''); setAuthError(''); setAuthorization(null); setHelp(null); command.reset()
  }, [selectedId])

  // OAuth state is verified by the backend. A callback query parameter alone is not a successful grant.
  useEffect(() => {
    if (!callback) return
    void connections.refetch()
    setNotice(callback === 'cancelled' ? 'Company authorization was cancelled. No new authorization is assumed.' : callback === 'failed' ? 'Company authorization did not complete. Check the connection and try again.' : 'Returned from company authorization. The connection status below is reported by the server.')
    const next = new URLSearchParams(location.search); next.delete('mcpOAuth')
    navigate(`${location.pathname}?${next}`, { replace: true })
    if (window.name === 'loomai-mcp-business-oauth') window.close()
  }, [callback])
  useEffect(() => {
    if (!authorization?.popup) return
    let ticks = 0
    const timer = window.setInterval(() => {
      ticks++
      if (authorization.popup!.closed) {
        setAuthorization(null); void connections.refetch(); setNotice('Authorization window closed. Check the server-reported connection status before discovering tools.')
      } else if (ticks % 4 === 0) void connections.refetch()
      if (authorization.popup!.closed || ticks >= 180 || Date.parse(authorization.expiresAt) <= Date.now()) window.clearInterval(timer)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [authorization])
  const beginAuthorization = async () => {
    if (!selected || authorizing || command.isPending) return
    const popup = window.open('about:blank', 'loomai-mcp-business-oauth', 'popup,width=620,height=760')
    if (popup) { popup.document.title = 'Connecting your company account'; popup.document.body.textContent = 'Preparing secure company authorization...'; popup.opener = null }
    setAuthorizing(true); setAuthError(''); setAuthorization(null)
    try {
      const response = await mcpApi.authorize(brainId, targetProfileId, selected.id, selected.revision, listHref(selected.id))
      const url = safeAuthorizationUrl(response.authorizationUrl)
      if (!live.current || activeSelection.current !== selected.id) { popup?.close(); return }
      setAuthorization({ url, expiresAt: response.expiresAt, popup })
      if (popup && !popup.closed) popup.location.replace(url)
    } catch (failure) {
      popup?.close(); if (live.current) setAuthError(failure instanceof Error ? failure.message : 'Company authorization could not be started.')
    } finally { if (live.current) setAuthorizing(false) }
  }
  const busy = command.isPending || authorizing
  const observed = selected?.status === 'ACTIVE' ? snapshot ?? savedSnapshot.data : null
  const currentSnapshot = observed?.connectionId === selected?.id && observed?.connectionRevision === selected?.revision ? observed : null
  const oauth = selected?.connectionModel === 'BUSINESS' && selected.auth.mode === 'OAUTH_AUTHORIZATION_CODE'
  const hasPanel = creating || Boolean(selectedId)
  return <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, minHeight: 0, height: '100%', '& .MuiTypography-root': { overflowWrap: 'anywhere' } }}>
    <BrainPageHeader stackOnMobile title="Connected tools" action={<Stack direction="row" gap={1} flexWrap="wrap">
      <Button component={RouterLink} to={href('capabilities')} startIcon={<ArrowBackRounded />}>Capabilities</Button>
      <Button variant="contained" component={RouterLink} to={listHref('connect')} disabled={!targetProfileId || !canManage || busy} startIcon={<AddRounded />}>Connect MCP</Button>
    </Stack>} />
    {!targetProfileId ? <Alert severity="info">Select the environment in the Smart Brain header to manage its connections.</Alert> : <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: hasPanel ? 'minmax(240px, .65fr) minmax(360px, 1.35fr)' : 'minmax(0, 1fr)' }, minHeight: 0, flex: 1 }}>
      <Stack gap={2} sx={{ minWidth: 0, minHeight: 0, pr: { lg: hasPanel ? 3 : 0 }, display: { xs: hasPanel ? 'none' : 'flex', lg: 'flex' } }}>
        <Stack direction="row" gap={1} alignItems="center" sx={{ borderBottom: 1, borderColor: 'divider', pb: 1.5 }}>
          <CableRounded color="primary" /><Typography variant="subtitle2" sx={{ flex: 1 }}>MCP connections{connections.isSuccess ? ` (${connections.data.connections.length})` : ''}</Typography>
          <Tooltip title="Refresh saved connection status"><IconButton size="small" aria-label="Refresh connection status" disabled={connections.isFetching || busy} onClick={() => void connections.refetch()}><RefreshRounded fontSize="small" /></IconButton></Tooltip>
        </Stack>
        {connections.isPending ? <CircularProgress size={24} aria-label="Loading MCP connections" /> : connections.isError ? <Alert severity="error" action={<Button onClick={() => void connections.refetch()}>Retry</Button>}>{connections.error.message}</Alert> : <Box sx={{ ...brainPaneSx, flex: 1 }}>
          <List disablePadding aria-label="MCP connections">{connections.data.connections.map(connection => <ListItemButton key={connection.id} component={RouterLink} to={listHref(connection.id)} selected={selected?.id === connection.id} disabled={busy} sx={{ minHeight: 86, gap: 1.5, borderBottom: 1, borderColor: 'divider' }}>
            {connection.connectionModel === 'PUBLIC' ? <PublicRounded fontSize="small" color="action" /> : <CableRounded fontSize="small" color="action" />}
            <ListItemText primary={connection.name} secondary={<>{connection.connectionModel === 'PUBLIC' ? 'Public / no upstream authentication' : 'Business-authorized'}<Typography variant="caption" component="span" display="block">{connectionEnabledInTarget(connection, targetProfileId) ? 'Enabled in this environment' : 'Not enabled in this environment'} / {statusText(connection.authStatus)}</Typography></>} primaryTypographyProps={{ fontWeight: 600 }} />
          </ListItemButton>)}</List>
          {!connections.data.connections.length ? <Stack gap={1} sx={{ py: 3 }}><Typography fontWeight={600}>{creating ? 'No saved connections yet' : 'Give your AI access to useful tools'}</Typography><Typography variant="body2" color="text.secondary">Connect a public information service or your company's MCP server, then choose the capabilities to add.</Typography>{!creating ? <Button component={RouterLink} to={listHref('connect')} disabled={!canManage} startIcon={<AddRounded />} sx={{ alignSelf: 'flex-start' }}>Connect a service</Button> : null}</Stack> : null}
        </Box>}
      </Stack>
      {hasPanel ? <Stack component="section" aria-label="MCP connection details" gap={2} sx={{ ...brainPaneSx, borderLeft: { lg: 1 }, borderColor: 'divider', pl: { lg: 3 }, pb: 2 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center"><Button component={RouterLink} to={listHref()} startIcon={<ArrowBackRounded />} sx={{ display: { lg: 'none' } }}>Connections</Button><Box sx={{ flex: 1 }} /><Tooltip title="Close connection details"><IconButton component={RouterLink} to={listHref()} aria-label="Close connection details" size="small" disabled={busy}><CloseRounded fontSize="small" /></IconButton></Tooltip></Stack>
        {notice ? <Alert severity="info" onClose={() => setNotice('')}>{notice}</Alert> : null}
        {command.isError && command.variables?.kind !== 'undo' ? <Alert severity="error">{command.error.message}</Alert> : null}
        {authError ? <Alert severity="error">{authError}</Alert> : null}
        {creating || editing ? canManage ? <McpConnectionForm key={creating ? 'new' : editing!.id} brainId={brainId} initial={creating ? emptyConnection() : editing!} secretsHref={href('secrets')} busy={busy}
          onSave={input => command.mutate({ kind: 'save', input, original: creating ? null : editing })} onCancel={() => creating ? navigate(listHref()) : setEditing(null)} /> : <Alert severity="info">An administrator or delegated administrator can manage company connections.</Alert>
          : selected ? <>
            <Stack direction="row" gap={1} alignItems="flex-start" justifyContent="space-between"><Box sx={{ minWidth: 0 }}><Typography component="h2" fontSize={21} fontWeight={650}>{selected.name}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5, overflowWrap: 'anywhere' }}>{selected.endpointUrl}</Typography></Box>
              <Tooltip title="Edit connection"><span><IconButton aria-label="Edit connection" disabled={!canManage || busy} onClick={() => setEditing(selected)}><EditOutlined fontSize="small" /></IconButton></span></Tooltip>
            </Stack>
            <Stack direction="row" flexWrap="wrap" gap={1}>
              <Chip variant="outlined" size="small" label={selected.connectionModel === 'PUBLIC' ? 'Public / no credentials' : 'Business-authorized'} />
              <Chip variant="outlined" size="small" color={connected(selected) ? 'success' : 'default'} label={`Authorization: ${statusText(selected.authStatus)}`} />
              <Chip variant="outlined" size="small" label={`Connection: ${statusText(selected.status)}`} />
            </Stack>
            <Typography variant="body2" color="text.secondary">{selected.connectionModel === 'PUBLIC' ? 'No upstream login is required. Customer sign-in and tool permissions are configured separately.' : 'Used by this Smart Brain\'s authorized actions. Your customers do not connect a personal upstream account; private operations still require verified identity and ownership checks.'}</Typography>
            <Box sx={{ py: 1.5, borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
              <FormControlLabel sx={{ m: 0 }} control={<Switch inputProps={{ role: 'switch' }} checked={connectionEnabledInTarget(selected, targetProfileId)} disabled={!canManage || busy || selected.status !== 'ACTIVE'}
                onChange={(_, enabled) => { command.reset(); setBindingChange(enabled) }} />} label="Use this connection in this environment" />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: .5 }}>Environments share this connection's endpoint and company authorization only when explicitly enabled. Existing imported tools are reused; credentials are not copied.</Typography>
              {!connectionEnabledInTarget(selected, targetProfileId) ? <Typography variant="caption" color="warning.main" display="block" sx={{ mt: .5 }}>Not enabled here. Reviewing tools does not authorize runtime use in this environment.</Typography> : null}
            </Box>
            {selected.authStatus === 'DISCONNECTED_UPSTREAM_REVOCATION_FAILED' ? <Alert severity="warning">Local access is disabled, but the upstream provider did not confirm revocation. Revoke the company's grant with that provider before reconnecting.</Alert> : null}
            {selected.auth.oauth?.scopes?.length ? <Typography variant="caption">Configured scopes: {selected.auth.oauth.scopes.join(', ')}</Typography> : null}
            {selected.tokenExpiresAt ? <Typography variant="caption" color="text.secondary">Current credential expires {new Date(selected.tokenExpiresAt).toLocaleString()}. Refresh and reconnect status are managed by the server.</Typography> : null}
            {oauth ? <Stack gap={1}>
              <Button variant={connected(selected) ? 'outlined' : 'contained'} startIcon={<OpenInNewRounded />} disabled={!canManage || busy} onClick={() => void beginAuthorization()} sx={{ alignSelf: 'flex-start' }}>{authorizing ? 'Preparing authorization...' : connected(selected) ? 'Reconnect company account' : 'Connect for this Smart Brain'}</Button>
              {authorization ? <Alert severity="info">{authorization.popup ? 'Complete company authorization in the provider window. This page checks saved status only.' : 'The authorization window was blocked. Continue in this tab to complete company authorization.'}<Button size="small" disabled={Date.parse(authorization.expiresAt) <= Date.now()} onClick={() => window.location.assign(authorization.url)}>Continue in this tab</Button></Alert> : null}
            </Stack> : null}
            <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center" sx={{ py: 1 }}>
              <Button variant="outlined" startIcon={<RefreshRounded />} disabled={!canManage || busy || selected.status !== 'ACTIVE' || Boolean(oauth && !connected(selected))} onClick={() => { setReceipt(null); command.mutate({ kind: 'discover', connection: selected }) }}>{command.isPending && command.variables?.kind === 'discover' ? 'Discovering tools...' : 'Discover / refresh tools'}</Button>
              <Button size="small" color="error" disabled={!canManage || busy || selected.status === 'DISCONNECTED'} onClick={() => setDisconnecting(true)}>Disconnect</Button>
            </Stack>
            <Typography variant="caption" color="text.secondary">{transportLabel[selected.transport] ?? selected.transport} / Revision {selected.revision}. Refresh checks metadata only; it does not execute tools or automatically update a draft.</Typography>
            <Divider />
            {receipt ? <Alert ref={receiptElement} severity="success" icon={<CheckCircleOutlineRounded />}><Typography variant="body2" fontWeight={600}>Draft import completed</Typography>
              <Typography variant="body2">{receipt.createdCount} created / {receipt.updatedCount} updated. Draft revision {receipt.draftRevision}. Nothing was published or applied.</Typography>
              <Typography variant="caption" display="block" sx={{ mt: .5 }}>Receipt {receipt.id}</Typography>
              {receipt.changes?.length ? <McpImportReview changes={receipt.changes} /> : null}
              <Button component={RouterLink} to={href('updates')} size="small">Review changes in Updates</Button>
              <Button size="small" startIcon={<UndoRounded />} disabled={busy || !canImport} onClick={() => command.mutate({ kind: 'undo', connection: selected, receiptId: receipt.id })}>Undo this import</Button>
              {command.isError && command.variables?.kind === 'undo' ? <Typography role="alert" variant="body2" color="error" sx={{ mt: 1 }}>{command.error.message}</Typography> : null}
              {receipt.actionNames.length ? <Link component={RouterLink} to={href(`capabilities/${encodeURIComponent(`action:${receipt.actionNames[0]}`)}`)} fontSize={13} sx={{ display: 'block', mt: .5 }}>Open imported action settings</Link> : null}
            </Alert> : null}
            {observed && !currentSnapshot ? <Alert severity="warning">The connection changed after discovery. Refresh tools before importing; the old snapshot is not used.</Alert> : null}
            {savedSnapshot.isError && !snapshot ? <Alert severity="warning">The saved tool snapshot is not available. Refresh tools to obtain current metadata. {savedSnapshot.error.message}</Alert> : null}
            {currentSnapshot ? <McpToolPicker key={currentSnapshot.id} snapshot={currentSnapshot} comparison={actionsConfig === undefined ? undefined : compareMcpTools(currentSnapshot, actionsConfig)} existingPolicies={existingToolPolicies(actionsConfig, selected.id)} busy={busy} canImport={canImport} onImport={input => command.mutate({ kind: 'import', connection: selected, input })} onHelp={setHelp} />
              : !command.isPending && !savedSnapshot.isFetching ? <Typography variant="body2" color="text.secondary">{selected.status === 'DISCONNECTED' ? 'Edit and save this connection to enable it again.' : selected.lastDiscoveryId ? 'A previous discovery is recorded. Refresh tools to review a current snapshot before importing.' : 'Discover tools to see what this service offers, including schemas and compatibility.'}</Typography> : <CircularProgress size={22} aria-label="Loading tool metadata" />}
          </> : connections.isPending ? <CircularProgress size={24} aria-label="Loading connection" /> : connections.isError ? <Alert severity="error">Connection details could not be loaded. Retry the connection list.</Alert> : <Alert severity="warning">This connection is not available in the selected Brain environment.</Alert>}
      </Stack> : null}
    </Box>}
    <Dialog open={disconnecting} onClose={() => { if (!busy) setDisconnecting(false) }} maxWidth="sm" fullWidth><DialogTitle>Disconnect {selected?.name}?</DialogTitle><DialogContent>Its upstream authorization will be invalidated and actions using this connection in all enabled environments will no longer be able to use it. Saved action definitions are retained.
      {command.isError && command.variables?.kind === 'disconnect' ? <Alert severity="error" sx={{ mt: 2 }}>{command.error.message}</Alert> : null}
    </DialogContent><DialogActions><Button disabled={busy} onClick={() => setDisconnecting(false)}>Keep connection</Button><Button variant="contained" color="error" disabled={busy || !selected} onClick={() => selected && command.mutate({ kind: 'disconnect', connection: selected })}>{busy ? 'Disconnecting...' : 'Disconnect'}</Button></DialogActions></Dialog>
    <Dialog open={bindingChange !== null} onClose={() => { if (!busy) setBindingChange(null) }} maxWidth="sm" fullWidth><DialogTitle>{bindingChange ? 'Enable this connection here?' : 'Disable this connection here?'}</DialogTitle>
      <DialogContent><Stack gap={2}>
        <Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{selected?.name}</Typography><Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{selected?.endpointUrl}</Typography>
        <Typography variant="body2">{bindingChange ? 'Allow the selected environment to use this same endpoint and company authorization. Existing imported actions are reused without copying credentials. This does not deploy or verify a release, or import tools automatically.' : 'Runtime access through this connection will stop immediately in the selected environment. Other environment bindings and saved action definitions remain unchanged.'}</Typography>
        {command.isError && command.variables?.kind === 'binding' ? <Alert severity="error">{command.error.message}</Alert> : null}
      </Stack></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setBindingChange(null)}>Cancel</Button><Button variant="contained" color={bindingChange ? 'primary' : 'error'} disabled={busy || !selected} onClick={() => selected && bindingChange !== null && command.mutate({ kind: 'binding', connection: selected, enabled: bindingChange })}>{busy ? 'Saving...' : bindingChange ? 'Enable connection here' : 'Disable connection here'}</Button></DialogActions>
    </Dialog>
    {help && currentSnapshot && selected ? <McpAssistant brainId={brainId} connectionId={selected.id} snapshotId={currentSnapshot.id} toolNames={help} onClose={() => setHelp(null)} onSaved={refreshDraft} /> : null}
  </Box>
}
