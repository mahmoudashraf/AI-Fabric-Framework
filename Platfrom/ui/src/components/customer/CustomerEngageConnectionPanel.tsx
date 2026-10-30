import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Chip, Collapse, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import LinkRounded from '@mui/icons-material/LinkRounded'
import TuneRounded from '@mui/icons-material/TuneRounded'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createAIWorkspaceConnection, fetchDeploymentReleases, fetchDeploymentTargetOptions,
  fetchPlatformAuthSession, type AIWorkspaceInstallationSummary, type CustomerDeploymentSummary,
  type PlatformConsumerSummary } from '../../api/platformApi'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'
import { connectableRelease, connectionEnvironments, connectionEnvironmentSelection,
  initialDeploymentConnection, matchingEngageConnections } from './customerEngageConnection'

export function CustomerEngageConnectionPanel({ open, onClose, customerId, actorId, deployments, consumers, apps,
  initialDeploymentId, currentConsumerId, currentApp, chooseApp = false, onConnected, onManageApp }: {
  open: boolean; onClose: () => void; customerId: string; actorId: string | null | undefined
  deployments: CustomerDeploymentSummary[]; consumers: PlatformConsumerSummary[]; apps: AIWorkspaceInstallationSummary[]
  initialDeploymentId?: string; currentConsumerId?: string; currentApp?: AIWorkspaceInstallationSummary | null
  chooseApp?: boolean
  onConnected: (consumer: PlatformConsumerSummary, app: AIWorkspaceInstallationSummary | null) => void
  onManageApp: (app: AIWorkspaceInstallationSummary) => void
}) {
  const queryClient = useQueryClient()
  const [deploymentId, setDeploymentId] = useState('')
  const [environment, setEnvironment] = useState('')
  const [targetId, setTargetId] = useState('')
  const [releaseId, setReleaseId] = useState('')
  const [connectionId, setConnectionId] = useState('')
  const [appId, setAppId] = useState('new')
  const [connectionOptionsOpen, setConnectionOptionsOpen] = useState(false)
  const [selectionIssue, setSelectionIssue] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const command = useRef<{ identity: string; key: string } | null>(null)
  const initializedSelection = useRef<string | null>(null)
  const active = useRef({ open, customerId, actorId })
  active.current = { open, customerId, actorId }
  const selectedApp = chooseApp ? apps.find(item => item.installationId === appId) ?? null : currentApp ?? null
  const assignmentId = selectedApp?.consumerId ?? currentConsumerId
  const current = consumers.find(item => item.consumerId === assignmentId && item.customerId === customerId)
  const deploymentCurrent = current?.boundDeploymentId === deploymentId ? current : undefined
  const bootstrapTargetId = deployments.find(item => item.deploymentId === deploymentId)?.bootstrapTargetProfileId
  useEffect(() => {
    if (!open) return
    const requested = consumers.find(item => item.consumerId === (currentApp?.consumerId ?? currentConsumerId)
      && item.customerId === customerId)
    setDeploymentId(initialDeploymentId ?? requested?.boundDeploymentId ?? '')
    setEnvironment('')
    setTargetId(''); setReleaseId(''); setSelectionIssue(null); setConnectionOptionsOpen(false)
    initializedSelection.current = null
    setConnectionId(''); setAppId(currentApp?.installationId ?? 'new'); setError(null); command.current = null
  }, [open, customerId, actorId]) // Selection is captured once when the panel opens.
  const targets = useQuery({ queryKey: ['engage-connection-targets', customerId, deploymentId],
    queryFn: () => fetchDeploymentTargetOptions(deploymentId), enabled: open && Boolean(deploymentId), retry: false })
  const releases = useQuery({ queryKey: ['engage-connection-releases', customerId, deploymentId],
    queryFn: () => fetchDeploymentReleases(deploymentId), enabled: open && Boolean(deploymentId), retry: false })
  const selectionScope = JSON.stringify([customerId, actorId, deploymentId, selectedApp?.installationId ?? null])
  useEffect(() => {
    if (!open || !targets.isSuccess || !releases.isSuccess || initializedSelection.current === selectionScope) return
    const selection = initialDeploymentConnection(targets.data, releases.data, consumers, customerId,
      deploymentId, assignmentId, bootstrapTargetId)
    initializedSelection.current = selectionScope
    setEnvironment(targets.data.find(item => item.id === selection.targetId)?.environmentName ?? '')
    setTargetId(selection.targetId); setReleaseId(selection.releaseId); setSelectionIssue(selection.issue)
    setConnectionOptionsOpen(false)
  }, [open, targets.data, releases.data, consumers, selectionScope, assignmentId, bootstrapTargetId])
  const environments = connectionEnvironments(targets.data ?? [])
  const environmentReleases = releases.data?.filter(item => targets.data?.some(target => target.active
    && target.environmentName === environment && target.id === item.targetProfileId)) ?? []
  const matches = matchingEngageConnections(consumers, customerId, deploymentId, targetId, releaseId || null)
  useEffect(() => {
    setConnectionId(matches.some(item => item.consumerId === assignmentId) ? assignmentId!
      : matches.length === 1 ? matches[0].consumerId : matches.length ? '' : 'new')
  }, [deploymentId, targetId, releaseId, assignmentId, matches.map(item => item.consumerId).join('|')])
  const selectedRelease = releases.data?.find(item => item.id === releaseId && item.targetProfileId === targetId)
  const validTarget = targets.data?.some(item => item.id === targetId && item.active && item.environmentName === environment)
  const readError = targets.error || releases.error
  const canConnect = Boolean(deploymentId && validTarget && connectionId && !readError && !selectionIssue && !targets.isFetching
    && !releases.isFetching && (!releaseId || (selectedRelease && connectableRelease(selectedRelease)))
    && selectedApp?.status !== 'ACTIVE')
  const connect = async () => {
    setPending(true); setError(null)
    const scope = { customerId, actorId }
    try {
      const session = await fetchPlatformAuthSession()
      if (!active.current.open || active.current.customerId !== scope.customerId || active.current.actorId !== scope.actorId
        || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || !isCustomerAuthenticationMode(session.authenticationMode)
        || session.customerId !== scope.customerId || session.actorId !== scope.actorId) {
        throw new Error('The customer account changed. Reopen Connect deployment in the current account.')
      }
      if (!canConnect) throw new Error('Review the deployment connection before continuing.')
      let connection = matches.find(item => item.consumerId === connectionId)
      if (!connection) {
        const payload = { deploymentId, targetProfileId: targetId, releaseId: releaseId || null,
          displayName: `${deployments.find(item => item.deploymentId === deploymentId)?.name ?? 'Engage'} connection`.slice(0, 255) }
        const identity = JSON.stringify([customerId, payload])
        if (command.current?.identity !== identity) command.current = { identity, key: crypto.randomUUID() }
        connection = await createAIWorkspaceConnection(customerId, { ...payload, operationKey: command.current.key })
        await queryClient.invalidateQueries({ queryKey: ['platform-customers'] })
      }
      if (!active.current.open || active.current.customerId !== scope.customerId || active.current.actorId !== scope.actorId) return
      onConnected(connection, selectedApp)
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'The connection could not be saved.') }
    finally { setPending(false) }
  }
  return <Dialog open={open} onClose={pending ? undefined : onClose} fullWidth maxWidth="sm">
    <DialogTitle>Connect deployment</DialogTitle>
    <DialogContent dividers><Stack spacing={2.5}>
      {chooseApp ? <TextField select label="Engage app" value={appId} disabled={pending} onChange={event => setAppId(event.target.value)}>
        <MenuItem value="new">Create a new Engage app</MenuItem>
        {apps.map(app => <MenuItem key={app.installationId} value={app.installationId}>{app.displayName} · {app.status}</MenuItem>)}
      </TextField> : null}
      <TextField select label="AI deployment" value={deploymentId} disabled={pending} onChange={event => {
        setDeploymentId(event.target.value); setEnvironment(''); setTargetId(''); setReleaseId(''); setError(null); setSelectionIssue(null)
      }}><MenuItem value="">Choose a deployment</MenuItem>{deployments.map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}</TextField>
      {deploymentId && targets.isSuccess && releases.isSuccess ? <Stack spacing={1}>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
          {environment ? <Chip label={environments.find(item => item.name === environment)?.label ?? environment} size="small" variant="outlined" /> : null}
          <Typography variant="body2" color="text.secondary">{selectionIssue ? 'Connection needs a choice'
            : selectedRelease ? connectableRelease(selectedRelease) ? 'Verified runtime' : 'Runtime verification needed'
              : releaseId ? 'Runtime release unavailable' : 'Configuration draft'}</Typography>
        </Stack>
        {selectedRelease ? <Typography variant="caption" color="text.secondary">Released {new Date(selectedRelease.createdAt).toLocaleString()}</Typography> : null}
        <Button startIcon={<TuneRounded />} size="small" sx={{ alignSelf: 'flex-start' }} disabled={pending}
          aria-expanded={connectionOptionsOpen} aria-controls="engage-connection-options"
          onClick={() => setConnectionOptionsOpen(value => !value)}>{connectionOptionsOpen ? 'Hide connection options' : 'Change connection'}</Button>
      </Stack> : null}
      {selectionIssue ? <Alert severity="warning">{selectionIssue}</Alert> : null}
      <Collapse in={connectionOptionsOpen} unmountOnExit><Stack spacing={2.5} id="engage-connection-options">
      <TextField select label="Environment" value={environments.some(item => item.name === environment) ? environment : ''}
        disabled={pending || !targets.isSuccess} onChange={event => {
          setEnvironment(event.target.value); setSelectionIssue(null)
          const selection = connectionEnvironmentSelection(event.target.value, targets.data ?? [],
            releases.data ?? [], deploymentCurrent, bootstrapTargetId)
          setTargetId(selection.targetId); setReleaseId(selection.releaseId)
        }}><MenuItem value="">Choose an environment</MenuItem>{environments.map(item => <MenuItem key={item.name} value={item.name}>{item.label}</MenuItem>)}</TextField>
      <TextField select label="Runtime release" value={environmentReleases.some(item => item.id === releaseId) ? releaseId : ''} InputLabelProps={{ shrink: true }} SelectProps={{ displayEmpty: true }} disabled={pending || !environment || !releases.isSuccess} onChange={event => {
        setSelectionIssue(null)
        const selected = environmentReleases.find(item => item.id === event.target.value)
        if (selected) { setReleaseId(selected.id); setTargetId(selected.targetProfileId!); return }
        const selection = connectionEnvironmentSelection(environment, targets.data ?? [], [], deploymentCurrent, bootstrapTargetId)
        setReleaseId(''); setTargetId(selection.targetId)
      }}>
        <MenuItem value="">Configure draft - no live release</MenuItem>
        {environmentReleases.map(item => <MenuItem key={item.id} value={item.id} disabled={!connectableRelease(item)}>{new Date(item.createdAt).toLocaleString()} · {connectableRelease(item) ? 'Ready' : 'Verification needed'}</MenuItem>)}
      </TextField>
      {environment && !targetId && targets.isSuccess && releases.isSuccess ? <Alert severity="warning">This environment needs a deployment target configured before you can connect an app.</Alert> : null}
      </Stack></Collapse>
      {!releaseId && targetId ? <Alert severity="info">You can save and preview an app now. Activation waits for a verified runtime release.</Alert> : null}
      {matches.length > 1 ? <TextField select label="Existing connection" value={connectionId} disabled={pending} onChange={event => setConnectionId(event.target.value)}>
        <MenuItem value="">Choose a connection</MenuItem>{matches.map(item => <MenuItem key={item.consumerId} value={item.consumerId}>{item.displayName}</MenuItem>)}<MenuItem value="new">Create a separate connection</MenuItem>
      </TextField> : null}
      {selectedApp ? <Typography variant="body2">Only {selectedApp.displayName} will use the selected connection after you save its configuration. Other apps will not move.</Typography> : null}
      {selectedApp?.status === 'ACTIVE' ? <Alert severity="warning" action={<Button onClick={() => onManageApp(selectedApp)}>App settings</Button>}>Disable this app in its settings before changing its deployment. Its current connection has not changed.</Alert> : null}
      {connectionId && connectionId !== 'new' ? <Typography variant="caption" color="text.secondary">Using an existing connection. {apps.filter(app => app.consumerId === connectionId).length} app(s) currently use it; its assignment will not be changed.</Typography> : null}
      {readError || error ? <Alert severity="error">{error ?? (readError instanceof Error ? readError.message : 'Connection options could not be loaded.')}</Alert> : null}
    </Stack></DialogContent>
    <DialogActions><Button disabled={pending} onClick={onClose}>Cancel</Button><Button variant="contained" startIcon={<LinkRounded />} disabled={pending || !canConnect} onClick={() => void connect()}>{pending ? 'Connecting...' : 'Use this deployment'}</Button></DialogActions>
  </Dialog>
}
