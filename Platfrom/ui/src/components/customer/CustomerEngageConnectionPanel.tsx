import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import LinkRounded from '@mui/icons-material/LinkRounded'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createAIWorkspaceConnection, fetchDeploymentReleases, fetchDeploymentTargetOptions,
  fetchPlatformAuthSession, type AIWorkspaceInstallationSummary, type CustomerDeploymentSummary,
  type PlatformConsumerSummary } from '../../api/platformApi'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'
import { connectableRelease, initialConnectionRelease, matchingEngageConnections } from './customerEngageConnection'

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
  const [targetId, setTargetId] = useState('')
  const [releaseId, setReleaseId] = useState('')
  const [connectionId, setConnectionId] = useState('')
  const [appId, setAppId] = useState('new')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const command = useRef<{ identity: string; key: string } | null>(null)
  const active = useRef({ open, customerId, actorId })
  active.current = { open, customerId, actorId }
  const current = consumers.find(item => item.consumerId === currentConsumerId && item.customerId === customerId)
  useEffect(() => {
    if (!open) return
    setDeploymentId(initialDeploymentId ?? current?.boundDeploymentId ?? '')
    setTargetId(current?.boundTargetProfileId ?? '')
    setReleaseId(current?.boundReleaseId ?? '')
    setConnectionId(''); setAppId(currentApp?.installationId ?? 'new'); setError(null); command.current = null
  }, [open, customerId, actorId]) // Selection is captured once when the panel opens.
  const targets = useQuery({ queryKey: ['engage-connection-targets', customerId, deploymentId],
    queryFn: () => fetchDeploymentTargetOptions(deploymentId), enabled: open && Boolean(deploymentId), retry: false })
  const releases = useQuery({ queryKey: ['engage-connection-releases', customerId, deploymentId],
    queryFn: () => fetchDeploymentReleases(deploymentId), enabled: open && Boolean(deploymentId), retry: false })
  useEffect(() => {
    if (!targets.isSuccess || !releases.isSuccess) return
    if (!targetId && targets.data.length === 1) {
      const id = targets.data[0].id; setTargetId(id)
      setReleaseId(initialConnectionRelease(releases.data, id, current))
    }
  }, [targets.data, releases.data, targetId])
  const matches = matchingEngageConnections(consumers, customerId, deploymentId, targetId, releaseId || null)
  useEffect(() => {
    setConnectionId(matches.length === 1 ? matches[0].consumerId : matches.length ? '' : 'new')
  }, [deploymentId, targetId, releaseId, matches.map(item => item.consumerId).join('|')])
  const selectedApp = chooseApp ? apps.find(item => item.installationId === appId) ?? null : currentApp ?? null
  const selectedRelease = releases.data?.find(item => item.id === releaseId && item.targetProfileId === targetId)
  const validTarget = targets.data?.some(item => item.id === targetId && item.active)
  const readError = targets.error || releases.error
  const canConnect = Boolean(deploymentId && validTarget && connectionId && !readError && !targets.isFetching
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
      if (!canConnect) throw new Error('Review the deployment, environment and release before connecting.')
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
        setDeploymentId(event.target.value); setTargetId(''); setReleaseId(''); setError(null)
      }}><MenuItem value="">Choose a deployment</MenuItem>{deployments.map(item => <MenuItem key={item.deploymentId} value={item.deploymentId}>{item.name}</MenuItem>)}</TextField>
      <TextField select label="Environment" value={targets.data?.some(item => item.id === targetId) ? targetId : ''}
        disabled={pending || !targets.isSuccess} onChange={event => {
          setTargetId(event.target.value); setReleaseId(initialConnectionRelease(releases.data ?? [], event.target.value, current))
        }}><MenuItem value="">Choose an environment</MenuItem>{targets.data?.map(item => <MenuItem key={item.id} value={item.id}>{item.name} · {item.environmentName}</MenuItem>)}</TextField>
      <TextField select label="Runtime release" value={releaseId} InputLabelProps={{ shrink: true }} SelectProps={{ displayEmpty: true }} disabled={pending || !targetId || !releases.isSuccess} onChange={event => setReleaseId(event.target.value)}>
        <MenuItem value="">Configure draft - no live release</MenuItem>
        {releases.data?.filter(item => item.targetProfileId === targetId).map(item => <MenuItem key={item.id} value={item.id} disabled={!connectableRelease(item)}>{new Date(item.createdAt).toLocaleString()} · {connectableRelease(item) ? 'Ready' : 'Verification needed'}</MenuItem>)}
      </TextField>
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
