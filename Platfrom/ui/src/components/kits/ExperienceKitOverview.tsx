import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Tooltip, useTheme } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import EditOutlined from '@mui/icons-material/EditOutlined'
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchCustomerDeploymentPortfolio, fetchCustomerSelfServiceOffer, fetchDocumentStorageBindings, fetchDeploymentDesk, fetchDeploymentReleases, getPlatformApiBaseUrl,
  PlatformApiError, type AIWorkspaceInstallationSummary, type AIWorkspaceReadinessCheck } from '../../api/platformApi'
import { experienceKitKeys, fetchExperienceKit, fetchExperienceKitCatalogue, updateExperienceKit, type ExperienceKitSummary } from '../../api/experienceKitsApi'
import { KitExperienceFields } from './KitExperienceFields'
import { KitMark } from './KitIdentity'
import { KitWorkspacePreview } from './KitWorkspacePreview'
import { KitSupportSpaceSetup } from './KitSupportSpaceSetup'
import { HostedDocumentStorageSetup } from '../documents/HostedDocumentStorageSetup'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { connectableRelease } from '../customer/customerEngageConnection'
import { kitAppPresentation, kitDeploymentPath, kitDisplayName, kitEditorPath, kitExperienceErrors, kitFormFromSummary, kitLatestAttempt, kitReadinessText, kitRelease, kitUpdateRequest, safeKitExternalUrl } from './kitModels'

export function ExperienceKitOverview({ customerId, id }: { customerId: string; id: string }) {
  const kit = useQuery({ queryKey: experienceKitKeys.detail(customerId, id), queryFn: ({ signal }) => fetchExperienceKit(id, signal), retry: false })
  if (kit.isPending) return <div className="ek-query-state" role="status"><CircularProgress size={24} />Loading your kit...</div>
  if (kit.isError) return <Alert severity="error" action={<Button onClick={() => void kit.refetch()}>Retry</Button>}>{kit.error instanceof Error ? kit.error.message : 'The kit could not be loaded.'}<Button component={Link} to="/kits">Back to My kits</Button></Alert>
  if ([kit.data.workspace, kit.data.embeddedWorkspace].some(workspace => workspace && workspace.customerId !== customerId)) {
    return <Alert severity="error">The returned installation does not match this customer account. Open the product editor to reload your resources.<Button component={Link} to="/ai-workspaces">Engage apps</Button></Alert>
  }
  return <KitOverviewData customerId={customerId} kit={kit.data} refresh={() => kit.refetch()} />
}

function WorkspaceStatus({ state }: { state: ReturnType<typeof kitAppPresentation> }) {
  return <span className={`ek-inline-status${state.ready ? ' ek-mint' : ' ek-amber'}`}><i />{state.label}
  </span>
}

function KitReadinessDetails({ checks }: { checks: AIWorkspaceReadinessCheck[] }) {
  return <details className="ek-advanced"><summary>Technical details</summary><ul>{checks.map((check, index) =>
    <li key={`${check.code}:${index}`}><strong>{check.code} ({check.status})</strong><p>{check.message}</p></li>)}</ul></details>
}

function KitOverviewData({ customerId, kit, refresh }: { customerId: string; kit: ExperienceKitSummary; refresh: () => Promise<unknown> }) {
  const [selected, setSelected] = useState<'deployment' | 'engage' | 'desk' | null>('engage')
  const [editing, setEditing] = useState(false)
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio, retry: false })
  const releases = useQuery({ queryKey: experienceKitKeys.releases(customerId, kit.deploymentId),
    queryFn: () => fetchDeploymentReleases(kit.deploymentId), retry: false })
  const deployment = portfolio.data?.find(item => item.deploymentId === kit.deploymentId)
  const release = kitRelease(kit, releases.data ?? [])
  const latestAttempt = kitLatestAttempt(kit, releases.data ?? [])
  const desk = useQuery({ queryKey: ['experience-kits', customerId, 'desk', kit.deploymentId, release?.targetProfileId, release?.id],
    queryFn: () => fetchDeploymentDesk({ deploymentId: kit.deploymentId, targetProfileId: release!.targetProfileId!, releaseId: release!.id }),
    enabled: Boolean(release?.targetProfileId), retry: false })
  const primary = kit.workspace
  const supportExperience = kit.kitCode === 'ai-support-experience'
  const channels: Array<{ key: string; name: string; app: AIWorkspaceInstallationSummary | null | undefined; embedded: boolean }> =
    kit.deliveryMode === 'both' ? [{ key: 'standalone', name: 'Standalone app', app: primary, embedded: false },
      { key: 'embedded', name: 'Website embed', app: kit.embeddedWorkspace, embedded: true }]
      : [{ key: kit.deliveryMode, name: kit.deliveryMode === 'embedded' ? 'Website embed' : 'Standalone app', app: primary, embedded: false }]
  const missingChannel = channels.some(channel => !channel.app)
  const appStates = channels.map(channel => kitAppPresentation(channel.app, kit.deploymentId, releases.data ?? []))
  const allReady = !missingChannel && appStates.every(state => state.ready)
  const allActive = allReady && appStates.every(state => state.active)
  const appearance = primary ? kitFormFromSummary(kit) : null
  const deskPath = `/human-support?${new URLSearchParams({ deploymentId: kit.deploymentId,
    ...(release?.targetProfileId ? { targetProfileId: release.targetProfileId, releaseId: release.id } : {}) })}`
  const deploymentPath = kitDeploymentPath(kit)
  const knowledgePath = `/document-knowledge?${new URLSearchParams({ deploymentId: kit.deploymentId })}`
  const integrationPath = `/integrations?${new URLSearchParams({ deploymentId: kit.deploymentId })}`
  const revisionPath = `/revisions?${new URLSearchParams({ deploymentId: kit.deploymentId,
    ...(release?.targetProfileId ? { targetProfileId: release.targetProfileId } : {}) })}`
  const refreshAll = () => { void refresh(); void portfolio.refetch(); void releases.refetch(); if (release?.targetProfileId) void desk.refetch() }
  const pending = portfolio.isFetching || releases.isFetching
  const activeUrl = primary?.status === 'ACTIVE' ? safeKitExternalUrl(primary.appUrl) : null
  const deskPortal = desk.data?.status === 'ENABLED' ? safeKitExternalUrl(desk.data.portalUrl) : null
  return <div className={`ek-overview${selected ? ' ek-overview-inspecting' : ''}`}>
    <div className="ek-overview-main"><Link to="/kits" className="ek-breadcrumb">My kits <span>/</span> {kit.name}</Link>
      <div className="ek-section-heading ek-overview-heading"><div><div className="ek-title-status"><h1>{kit.name}</h1><span className={`ek-badge${allReady ? ' ek-badge-mint' : ' ek-badge-amber'}`}>{missingChannel ? 'Setup needed' : allActive ? 'Live' : allReady ? 'Ready to go live' : 'Not live yet'}</span></div>
        <p>{kitDisplayName(kit.kitCode)}</p></div>
        <div className="ek-overview-commands"><Tooltip title="Refresh kit status"><IconButton aria-label="Refresh kit" onClick={refreshAll} disabled={pending}><RefreshRounded /></IconButton></Tooltip>
          {primary && kit.installationId && <Button component={Link} to={kitEditorPath(kit, true)} variant="outlined" startIcon={<OpenInNewRounded />}>Preview workspace</Button>}
        </div>
      </div>
      <div className="ek-overview-progress" aria-label="Kit setup progress">{[
        ['Your setup', true], ['Your workspace', !missingChannel && Boolean(deployment)], ['Final checks', allReady], ['Go live', allActive],
      ].map(([label, done], index) => <div key={String(label)} data-complete={Boolean(done)}><span>{done ? <CheckCircleRounded /> : index + 1}</span><strong>{label}</strong></div>)}</div>
      <p className="ek-overview-intro">Your AI experience, connected to your business.</p>
      {missingChannel && <Alert severity="warning">An Engage app is missing. Review your apps to continue setup.<Button component={Link} to="/ai-workspaces">Open Engage apps</Button></Alert>}
      {portfolio.isError && <Alert severity="error">Deployment details could not be loaded. <Button onClick={() => void portfolio.refetch()}>Retry</Button></Alert>}
      {portfolio.isSuccess && !deployment && <Alert severity="warning">The linked deployment is not available in your portfolio.<Button component={Link} to="/deployments">Open deployments</Button></Alert>}
      {releases.isError && <Alert severity="error">Deployment verification could not be loaded.<Button onClick={() => void releases.refetch()}>Retry</Button></Alert>}
      {primary?.releaseId && releases.isSuccess && !release && <Alert severity="warning">Your app's assigned release is unavailable. Open the deployment to review it.</Alert>}
      <div className="ek-product-resources">
        <article className={`ek-product-resource${selected === 'deployment' ? ' ek-resource-selected' : ''}`}><button type="button" onClick={() => setSelected('deployment')} aria-label="Inspect AI deployment">
          <KitMark product="platform" large /><div><h2>Your AI assistant</h2><p>{deployment?.name ?? kit.name}</p></div></button>
          <div className="ek-resource-body"><span className="ek-inline-status"><i />{releases.isPending ? 'Checking setup' : releases.isError ? 'Status unavailable' : release ? connectableRelease(release) ? 'Tested and connected' : 'Checks needed' : latestAttempt ? 'Setup needs attention' : 'Not live yet'}</span>
            <p>Answers and next steps using your business information</p><Link to={deploymentPath}>Set up your assistant <ArrowForwardRounded /></Link></div>
        </article><ArrowForwardRounded className="ek-resource-arrow" />
        <article className={`ek-product-resource${selected === 'engage' ? ' ek-resource-selected' : ''}`}><button type="button" onClick={() => setSelected('engage')} aria-label="Inspect Engage experience">
          <KitMark product="engage" large /><div><h2>Your customer workspace</h2><p>{primary?.displayName ?? 'Workspace missing'}</p></div></button>
          <div className="ek-resource-body"><WorkspaceStatus state={appStates[0]} /><p>{kit.deliveryMode === 'both' ? 'Website widget + standalone' : kit.deliveryMode === 'embedded' ? 'Website widget' : 'Standalone app'}</p>
            <button type="button" className="ek-text-button" onClick={() => setSelected('engage')}>View details <ArrowForwardRounded /></button></div>
        </article><ArrowForwardRounded className="ek-resource-arrow" />
        <article className={`ek-product-resource${selected === 'desk' ? ' ek-resource-selected' : ''}`}><button type="button" onClick={() => setSelected('desk')} aria-label="Inspect human support">
          <KitMark product="desk" large /><div><h2>{supportExperience ? 'Request tracking & team' : 'Human support'}</h2><p>LoomAI Desk</p></div></button>
          <div className="ek-resource-body"><span className={`ek-inline-status${desk.data?.status === 'ENABLED' ? ' ek-mint' : ' ek-amber'}`}><i />
            {supportExperience ? kit.ticketingEnabled ? 'Request tracking requested' : kit.humanSupportEnabled ? 'Human support requested' : 'Optional'
              : desk.isError ? 'Status unavailable' : desk.data?.status === 'ENABLED' ? 'Desk connected' : kit.humanSupportEnabled ? 'Setup requested' : 'Optional'}</span>
            <p>{supportExperience ? `Request tracking ${kit.ticketingEnabled ? 'requested' : 'off'}. Human support is separate.` : 'A person when your customers need one'}</p>
            {supportExperience ? <button type="button" className="ek-text-button" onClick={() => setSelected('desk')}>Configure support <ArrowForwardRounded /></button>
              : <Link to={deskPath}>Add your support team <ArrowForwardRounded /></Link>}</div>
        </article>
      </div>
      <div className="ek-resource-bands"><Link to={knowledgePath}><DescriptionOutlined /><span><strong>Your business information</strong><small>The approved documents your assistant can use</small></span><b>Manage documents</b><ArrowForwardRounded /></Link>
        <Link to={integrationPath}><ExtensionOutlined /><span><strong>Your connected systems</strong><small>Keep answers and next steps connected to your business</small></span><b>Manage connections</b><ArrowForwardRounded /></Link></div>
      <KitDocumentStorageSetup customerId={customerId} kit={kit} knowledgePath={knowledgePath} />
      <section className="ek-before-launch" aria-labelledby="ek-before-launch-title"><h2 id="ek-before-launch-title">Before you launch</h2>
        <div className="ek-readiness-list">{channels.map(channel => <div className="ek-readiness-channel" key={channel.key}><h3>{channel.name}</h3>
          {!channel.app ? <p className="ek-amber"><WarningAmberRounded />Installation unavailable</p>
            : channel.app.readinessChecks.length ? channel.app.readinessChecks.map((check, index) => <div key={`${check.code}:${index}`} className={check.status === 'PASSED' ? 'ek-readiness-passed' : 'ek-readiness-blocked'}>
              {check.status === 'PASSED' ? <CheckCircleRounded /> : <WarningAmberRounded />}<span>{kitReadinessText(check)}</span><small>{check.status === 'PASSED' ? 'Complete' : check.status === 'BLOCKED' ? 'Action needed' : check.status}</small>
            </div>) : <p className="ek-muted">Complete setup to check this workspace.</p>}
          {channel.app && <KitReadinessDetails checks={channel.app.readinessChecks} />}
        </div>)}</div>
        <p className="ek-muted">Finish these steps, then choose when to make your workspace live in Engage.</p>
      </section>
      <Link className="ek-help-link" to="/support">Get setup help <ArrowForwardRounded /></Link>
      <div className="ek-overview-advanced"><Button startIcon={<SettingsOutlined />} onClick={() => setEditing(true)} disabled={missingChannel}>Edit your experience</Button>
        <Button component={Link} to={revisionPath} endIcon={<ArrowForwardRounded />}>Versions & launch</Button></div>
      <p className="ek-observed">Last updated {new Date(kit.updatedAt).toLocaleString()}.</p>
    </div>
    {selected && <aside className="ek-inspector" aria-label="Selected product details"><div className="ek-inspector-heading"><KitMark product={selected === 'deployment' ? 'platform' : selected === 'desk' ? 'desk' : 'engage'} />
      <div><h2>{selected === 'engage' ? primary?.displayName ?? 'Customer workspace' : selected === 'deployment' ? deployment?.name ?? 'AI assistant' : supportExperience ? 'Support setup' : 'Human support'}</h2><small>{selected === 'engage' ? 'LoomAI Engage' : selected === 'deployment' ? 'LoomAI Platform' : 'LoomAI Desk'}</small></div>
      <Tooltip title="Close details"><IconButton aria-label="Close kit product details" onClick={() => setSelected(null)}><CloseRounded /></IconButton></Tooltip></div>
      {selected === 'engage' ? <>
        {channels.map((channel, index) => <section className="ek-inspector-section" key={channel.key}><h3>{channel.name}</h3>
          <WorkspaceStatus state={appStates[index]} />
          {channel.app && (channel.embedded ? kit.embeddedInstallationId : kit.installationId) ? <>
            {channel.app.readinessChecks.filter(check => check.status !== 'PASSED').map((check, index) => <Alert key={`${check.code}:${index}`} severity={check.status === 'BLOCKED' ? 'warning' : 'info'}>{kitReadinessText(check)}</Alert>)}
            <KitReadinessDetails checks={channel.app.readinessChecks} />
            <p className="ek-muted">{channel.name === 'Website embed' ? channel.app.allowedOrigins.join(', ') || 'No website origin configured' : channel.app.appUrl || 'App address not assigned'}</p>
            <Button component={Link} to={kitEditorPath(kit, false, channel.embedded)} variant="outlined" startIcon={<EditOutlined />}>Configure {channel.name.toLowerCase()}</Button>
            {channel.name === 'Website embed' && <KitInstallSnippet installationId={channel.app.installationId} />}
            {channel.app.domains.map(domain => <div className="ek-domain-fact" key={domain.hostname}><strong>{domain.hostname}</strong><span>{domain.status}</span>{domain.lastVerificationError && <p className="ek-amber">{domain.lastVerificationError}</p>}</div>)}
          </> : <Alert severity="warning">This installation is missing.<Button component={Link} to="/ai-workspaces">Open Engage apps</Button></Alert>}
        </section>)}
        <section className="ek-inspector-section"><h3>Your AI assistant</h3><Link className="ek-connected-deployment" to={deploymentPath}><KitMark product="platform" /><div><strong>{deployment?.name ?? kit.deploymentId}</strong><small>{release ? 'Connected version' : 'Setup saved, not live yet'}</small></div><ArrowForwardRounded /></Link></section>
        {primary && appearance && <section className="ek-inspector-section"><h3>Customer preview</h3><KitWorkspacePreview compact packCode={primary.experiencePackCode} configuration={primary.configuration}
          workspaceName={primary.displayName} brandColor={appearance.brandColor} humanSupportEnabled={kit.humanSupportEnabled} ticketingEnabled={kit.ticketingEnabled} />
          <Button component={Link} to={kitEditorPath(kit, true)} endIcon={<OpenInNewRounded />}>See your customer experience</Button></section>}
        {activeUrl && <Button component="a" href={activeUrl} target="_blank" rel="noopener noreferrer" startIcon={<OpenInNewRounded />}>Open active app</Button>}
      </> : selected === 'deployment' ? <>
        <section className="ek-inspector-section"><h3>Configuration</h3><dl className="ek-review-facts"><div><dt>Deployment</dt><dd>{kit.deploymentId}</dd></div><div><dt>State</dt><dd>{deployment?.status ?? 'Unavailable'}</dd></div>
          <div><dt>Published version</dt><dd>{deployment?.activeVersionId ?? 'Not recorded'}</dd></div></dl><Button component={Link} to={deploymentPath} variant="outlined">Configure deployment</Button></section>
        <section className="ek-inspector-section"><h3>App-assigned release</h3>{release ? <dl className="ek-review-facts"><div><dt>Status</dt><dd>{release.status}</dd></div><div><dt>Verification</dt><dd>{release.verificationStatus}</dd></div><div><dt>Release</dt><dd>{release.id}</dd></div></dl>
          : <p>Deploy and verify, then connect this app.</p>}{release?.errorMessage && <Alert severity="error">{release.errorMessage}</Alert>}
          {!release && latestAttempt && <><h3>Latest deployment attempt</h3><p>{latestAttempt.status} / {latestAttempt.verificationStatus}</p><p className="ek-muted">Not yet connected to the app.</p>{latestAttempt.errorMessage && <Alert severity="warning">{latestAttempt.errorMessage}</Alert>}</>}
          <Button component={Link} to={revisionPath}>Versions & updates</Button></section>
      </> : <>
        {supportExperience && channels.map(channel => channel.app && <KitSupportSpaceSetup key={`${channel.app.installationId}:${channel.app.rowVersion}:${channel.app.releaseId}`}
          customerId={customerId} kit={kit} app={channel.app} releases={releases.data ?? []} onConfigured={() => void refresh()} />)}
        <section className="ek-inspector-section"><h3>Your support team</h3><p>Human support setup is {kit.humanSupportEnabled ? 'requested' : 'not requested'} for this kit.</p>
          {desk.isError && <Alert severity="warning">Desk connection state could not be loaded.<Button onClick={() => void desk.refetch()}>Retry</Button></Alert>}
          {desk.data && <p>Connection: <strong>{desk.data.status}</strong></p>}
          {!release && <p className="ek-muted">Test and connect your AI assistant before adding your support team.</p>}
          {desk.data?.errorMessage && <Alert severity="warning">{desk.data.errorMessage}</Alert>}
          <p className="ek-muted">Assign your own agents in Desk. Each agent uses their own account.</p>
          <Button component={Link} to={deskPath} variant="outlined">Manage human support</Button>
          {deskPortal && <Button component="a" href={deskPortal} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded />}>Open Desk portal</Button>}
        </section>
      </>}
    </aside>}
    <Dialog open={editing} onClose={() => setEditing(false)} maxWidth="sm" fullWidth PaperProps={{ className: 'ek-kit-dialog' }}>
      <KitEditForm key={`${kit.id}:${kit.revision}`} customerId={customerId} kit={kit} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); void refresh() }} />
    </Dialog>
  </div>
}

function KitDocumentStorageSetup({ customerId, kit, knowledgePath }: { customerId: string; kit: ExperienceKitSummary; knowledgePath: string }) {
  const { session } = usePlatformAuth()
  const scope = [customerId, session?.actorId, session?.authenticationMode, kit.deploymentId]
  const enabled = Boolean(session?.authenticated && session.customerId === customerId)
  const offer = useQuery({ queryKey: ['hosted-document-storage-targets', ...scope, kit.offerId],
    queryFn: () => fetchCustomerSelfServiceOffer(kit.offerId), enabled, retry: false })
  const bindings = useQuery({ queryKey: ['document-storage-bindings', kit.deploymentId, ...scope],
    queryFn: () => fetchDocumentStorageBindings(kit.deploymentId), enabled, retry: false })
  const targetProfiles = (offer.data?.targets ?? []).filter(target => target.capabilities.includes('BINDING_REPLACE'))
    .map(target => ({ id: target.id, name: target.intent === 'CUSTOMER_STAGING' ? 'Customer staging' : 'Customer production' }))
  return <section className="ek-before-launch" aria-label="Your business document setup">
    {offer.isError && <Alert severity="error" action={<Button onClick={() => void offer.refetch()}>Retry</Button>}>Allowed document-storage targets could not be loaded.</Alert>}
    {bindings.isError && <Alert severity="warning" action={<Button onClick={() => void bindings.refetch()}>Retry</Button>}>Current target bindings could not be loaded.</Alert>}
    <HostedDocumentStorageSetup customerId={customerId} deploymentId={kit.deploymentId} targetProfiles={targetProfiles}
      initialTargetProfileId={kit.targetProfileId} bindings={bindings.data ?? []} indexingHref={knowledgePath}
      canManage={enabled && session?.role === 'CUSTOMER_ADMIN'}
      byoStorage={<Button component={Link} to={knowledgePath} variant="outlined" startIcon={<DescriptionOutlined />}>Connect S3-compatible storage</Button>} />
  </section>
}

function KitInstallSnippet({ installationId }: { installationId: string }) {
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle')
  const script = `<script async src="${getPlatformApiBaseUrl()}/api/public/ai-workspace/install.js" data-installation-id="${installationId}"></script>`
  return <div className="ek-install-snippet"><div><strong>Website installation</strong><Tooltip title="Copy installation script"><IconButton size="small" aria-label="Copy website installation script" onClick={() => {
    void navigator.clipboard.writeText(script).then(() => setCopyState('copied')).catch(() => setCopyState('failed'))
  }}><ContentCopyRounded fontSize="small" /></IconButton></Tooltip></div><pre tabIndex={0}><code>{script}</code></pre>
    {copyState === 'copied' && <small role="status">Copied.</small>}{copyState === 'failed' && <small role="alert">Could not copy. Select the script above.</small>}
    <small>Activate this website app when setup is complete.</small>
  </div>
}

function KitEditForm({ customerId, kit, onClose, onSaved }: { customerId: string; kit: ExperienceKitSummary; onClose: () => void; onSaved: () => void }) {
  const { palette } = useTheme()
  const [form, setForm] = useState(() => kitFormFromSummary(kit))
  const [error, setError] = useState<Error | null>(null)
  const queryClient = useQueryClient()
  const supportExperience = kit.kitCode === 'ai-support-experience'
  const catalogue = useQuery({ queryKey: experienceKitKeys.catalogue(customerId), queryFn: ({ signal }) => fetchExperienceKitCatalogue(signal), enabled: supportExperience, retry: false })
  const ticketProviders = catalogue.data?.find(entry => entry.definition.code === kit.kitCode)?.ticketProviders ?? []
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const save = useMutation({ mutationFn: () => {
    if (!kit.workspace) throw new Error('The saved workspace is unavailable. Open the existing app editor.')
    return updateExperienceKit(kit.id, kitUpdateRequest(form, kit.revision, kit.workspace.rowVersion, kit.embeddedWorkspace?.rowVersion ?? null))
  } })
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const errors = kitExperienceErrors(form, supportExperience ? ticketProviders : undefined)
    if (errors.length) { setError(new Error(errors[0])); return }
    setError(null)
    try {
      const updated = await save.mutateAsync()
      if (!mounted.current) return
      queryClient.setQueryData(experienceKitKeys.detail(customerId, kit.id), updated)
      await Promise.all([queryClient.invalidateQueries({ queryKey: experienceKitKeys.list(customerId) }),
        queryClient.invalidateQueries({ queryKey: ['ai-workspace-installations', customerId] })])
      if (mounted.current) onSaved()
    } catch (failure) { if (mounted.current) setError(failure instanceof Error ? failure : new Error('The kit could not be updated.')) }
  }
  const conflict = error instanceof PlatformApiError && error.status === 409
  const active = kit.workspace?.status === 'ACTIVE' || kit.embeddedWorkspace?.status === 'ACTIVE'
  return <form className="ek-page" noValidate data-kit-theme={palette.mode} onSubmit={event => void submit(event)}><DialogTitle>Edit your experience</DialogTitle><DialogContent>
    {active && <Alert severity="info" sx={{ mb: 2 }}>Your app is live. Use its editor for appearance and delivery changes.</Alert>}
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error.message}{conflict && <><p>Review the latest app configuration in its editor before saving.</p>
      {kit.installationId && <Button component={Link} to={kitEditorPath(kit)}>Open app editor</Button>}
      {kit.embeddedInstallationId && <Button component={Link} to={kitEditorPath(kit, false, true)}>Open website editor</Button>}</>}</Alert>}
    {supportExperience && catalogue.isError && <Alert severity="warning" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>Ticket providers could not be loaded. Your settings have not changed.</Alert>}
    <KitExperienceFields includeName value={form} onChange={setForm} disabled={save.isPending || (supportExperience && !catalogue.isSuccess)} deploymentId={kit.deploymentId}
      supportExperience={supportExperience} ticketProviders={ticketProviders} />
  </DialogContent><DialogActions><Button onClick={onClose} disabled={save.isPending}>Cancel</Button><Button type="submit" variant="contained" disabled={save.isPending || !kit.workspace || (supportExperience && !catalogue.isSuccess)}>
    {save.isPending ? 'Saving...' : 'Save changes'}</Button></DialogActions></form>
}
