import { useEffect, useRef, useState } from 'react'
import { Alert, Button, CircularProgress, MenuItem, TextField } from '@mui/material'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { configureKitSupportSpace, experienceKitKeys, fetchKitSupportSpace, type ConfigureKitSupportSpaceRequest, type ExperienceKitSummary } from '../../api/experienceKitsApi'
import { fetchDeploymentDesk, PlatformApiError, type AIWorkspaceInstallationSummary, type DeploymentReleaseSummary } from '../../api/platformApi'
import { connectableRelease } from '../customer/customerEngageConnection'

function setupError(error: unknown) {
  if (error instanceof PlatformApiError) {
    if (error.status === 401) return 'Sign in again before configuring support.'
    if (error.status === 403) return 'This installation is not available to your customer account.'
    if (error.status === 409) return 'Setup changed or a prerequisite is incomplete. Refresh, review the assigned release and customer sign-in settings, then try again.'
  }
  return 'Support setup could not be confirmed. Refresh its current state before trying again.'
}

export function KitSupportSpaceSetup({ customerId, kit, app, releases, onConfigured }: {
  customerId: string; kit: ExperienceKitSummary; app: AIWorkspaceInstallationSummary; releases: DeploymentReleaseSummary[];
  onConfigured(): void
}) {
  const candidates = releases.filter(release => release.deploymentId === kit.deploymentId && release.targetProfileId && release.provisioningStatus === 'ACTIVE' && connectableRelease(release))
  const [selected, setSelected] = useState(app.releaseId ?? (candidates.length === 1 ? candidates[0].id : ''))
  const release = candidates.find(item => item.id === selected)
  const key = experienceKitKeys.supportSpace(customerId, app.installationId, release?.targetProfileId ?? '', selected)
  const queryClient = useQueryClient()
  const status = useQuery({ queryKey: key, queryFn: ({ signal }) => fetchKitSupportSpace(customerId, app.installationId, release!.targetProfileId!, release!.id, signal),
    enabled: Boolean(release), retry: false })
  const human = useQuery({ queryKey: ['experience-kits', customerId, 'desk', kit.deploymentId, release?.targetProfileId, release?.id],
    queryFn: () => fetchDeploymentDesk({ deploymentId: kit.deploymentId, targetProfileId: release!.targetProfileId!, releaseId: release!.id }),
    enabled: Boolean(release && kit.humanSupportEnabled), retry: false })
  const humanReady = human.data?.status === 'ENABLED'
  const deskPath = `/human-support?${new URLSearchParams({ deploymentId: kit.deploymentId,
    ...(release?.targetProfileId ? { targetProfileId: release.targetProfileId, releaseId: release.id } : {}) })}`
  const operation = useRef<{ signature: string; key: string } | null>(null)
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const save = useMutation({ mutationFn: async () => {
    if (!status.data || !release || !kit.ticketProvider) throw new Error('Review support setup first.')
    const input: Omit<ConfigureKitSupportSpaceRequest, 'operationKey'> = { installationRevision: app.rowVersion,
      assignmentRevision: status.data.assignmentRevision, expectedVersion: status.data.space?.version ?? null,
      enabled: kit.ticketingEnabled, humanHandoverEnabled: kit.humanSupportEnabled, ticketProvider: kit.ticketProvider,
      closedRetentionDays: null, targetProfileId: release.targetProfileId!, releaseId: release.id }
    const signature = JSON.stringify([customerId, app.installationId, input])
    if (operation.current?.signature !== signature) operation.current = { signature, key: crypto.randomUUID() }
    return configureKitSupportSpace(customerId, app.installationId, { ...input, operationKey: operation.current.key })
  }, retry: false, onSuccess: async value => {
    if (!active.current) return
    queryClient.setQueryData(key, value)
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] }),
      queryClient.invalidateQueries({ queryKey: ['experience-kits', customerId, 'support-space'] })])
    if (active.current) onConfigured()
  } })
  const space = status.data?.space
  const matches = !!space && space.enabled === kit.ticketingEnabled && space.humanHandoverEnabled === kit.humanSupportEnabled && space.providerId === kit.ticketProvider
  return <section className="ek-inspector-section" aria-label={`Support setup for ${app.displayName}`}>
    <h3>{app.displayName}</h3>
    <p>Request tracking: <strong>{kit.ticketingEnabled ? 'Requested' : 'Off'}</strong>. Human support: <strong>{kit.humanSupportEnabled ? 'Requested' : 'Off'}</strong>.</p>
    {!kit.ticketProvider && <Alert severity="info">Choose a ticket provider in Edit your experience before configuring Desk.</Alert>}
    {!app.releaseId && candidates.length > 0 && <TextField size="small" select label="Verified support release" fullWidth value={selected} disabled={save.isPending}
      onChange={event => { setSelected(event.target.value); save.reset() }}>
      <MenuItem value="" disabled>Choose a verified release</MenuItem>{candidates.map(item => <MenuItem key={item.id} value={item.id}>{item.targetProfileId} &middot; {item.id}</MenuItem>)}
    </TextField>}
    {!release && <Alert severity="info">Publish, deploy and verify your AI assistant before configuring this support service.</Alert>}
    {release && !app.releaseId && <p className="ek-muted">Prepares this verified target only. It does not change your app assignment or make it live.</p>}
    {release && status.isPending && <CircularProgress size={20} aria-label="Checking support setup" />}
    {status.isError && <Alert severity="warning">{setupError(status.error)}</Alert>}
    {status.data && <dl className="ek-review-facts"><div><dt>Provider</dt><dd>{space?.providerId ?? kit.ticketProvider ?? 'Not selected'}</dd></div>
      <div><dt>Support service</dt><dd>{matches ? 'Configured for these settings' : space ? 'Settings update needed' : 'Not configured'}</dd></div>
      {space && <div><dt>Closed-request retention</dt><dd>{space.closedRetentionDays} days</dd></div>}</dl>}
    {kit.humanSupportEnabled && !humanReady && <Alert severity="info">Connect this target's human support channel and team first. Request tracking does not grant agent access.<Button component={Link} to={deskPath}>Manage human support</Button>
      {human.isError && <Button onClick={() => void human.refetch()}>Refresh human support</Button>}</Alert>}
    {save.isError && <Alert severity="warning">{setupError(save.error)}</Alert>}
    <div className="ek-support-setup-actions"><Button startIcon={<RefreshRounded />} disabled={!release || status.isFetching || save.isPending} onClick={() => { save.reset(); void status.refetch() }}>Refresh support setup</Button>
      <Button variant="outlined" startIcon={<SettingsOutlined />} disabled={!release || !status.data || status.isError || status.isFetching || save.isPending || !kit.ticketProvider
        || (kit.humanSupportEnabled && !humanReady) || matches || (!space && !kit.ticketingEnabled && !kit.humanSupportEnabled)} onClick={() => save.mutate()}>
        {save.isPending ? 'Configuring support...' : space ? 'Update support setup' : 'Configure support service'}</Button></div>
  </section>
}
