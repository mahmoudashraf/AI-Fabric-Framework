import { useState, type ReactNode } from 'react'
import { useLocation, useNavigate, Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Divider, Drawer, IconButton, Stack, TextField, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import WidgetsOutlined from '@mui/icons-material/WidgetsOutlined'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AddRounded from '@mui/icons-material/AddRounded'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary, DeploymentReleaseSummary } from '../../api/platformApi'
import { appResource, deploymentResource, releaseEnvironment, releaseIssues, safeCustomerAppUrl, type CustomerResource } from './customerResourceModels'
import { CustomerConfigurationPanel } from './CustomerConfigurationPanel'
import { CustomerDetailSections, type CustomerEditorSection } from './CustomerDetailSections'
import { CustomerProductHome } from './CustomerProductHome'
import { CustomerProductTasks } from './CustomerProductTasks'
import { CustomerDeploymentAdvancedLinks } from './CustomerDeploymentAdvanced'
import { CustomerDeskLink } from './CustomerDeskLink'

function ResourceFacts({ facts }: { facts: Array<[string, string]> }) {
  return <Box component="dl" sx={{ m: 0 }}>{facts.map(([label,value]) => <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}><Typography component="dt" variant="caption" color="text.secondary">{label}</Typography><Typography component="dd" variant="caption" sx={{ m: 0, textAlign: 'right', overflowWrap: 'anywhere', maxWidth: '62%' }}>{value}</Typography></Box>)}</Box>
}

export type CustomerResourceSurfaceProps = {
  mode: 'home' | 'deployments' | 'apps'
  deployments: CustomerDeploymentSummary[]
  apps: AIWorkspaceInstallationSummary[]
  offers: CustomerOfferSummary[]
  releases: DeploymentReleaseSummary[]
  loading: boolean
  releaseLoading: boolean
  errors: string[]
  releaseError?: string | null
  dataObservedAt: number | null
  configuration?: ReactNode
}

export function CustomerResourceSurface(props: CustomerResourceSurfaceProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const compact = useMediaQuery(useTheme().breakpoints.down('md'))
  const [search, setSearch] = useState('')
  const params = new URLSearchParams(location.search)
  const id = params.get('resourceId')
  const kind = params.get('resourceKind')
  const deploymentCards = props.deployments.map(item => deploymentResource(item, props.offers.find(offer => offer.id === item.offerId)))
  const appCards = props.apps.map(appResource)
  const allResources = [...deploymentCards, ...appCards]
  const selected = allResources.find(item => item.id === id && item.kind === kind)
  const unresolved = Boolean(id && !props.loading && !selected)
  const offer = props.offers.find(item => item.id === props.deployments.find(deployment => deployment.deploymentId === selected?.deploymentId)?.offerId)
  const selectedReleaseId = params.get('releaseId')
  const selectedRelease = props.releases.find(item => item.id === selectedReleaseId)
  const assignedApp = selected?.kind === 'app' ? props.apps.find(item => item.installationId === selected.id) : null
  const deskRelease = selected?.kind === 'app'
    ? props.releases.find(item => item.id === assignedApp?.releaseId) : selectedRelease
  const visibleReleases = selectedReleaseId ? selectedRelease ? [selectedRelease] : [] : props.releases
  const open = (resource: CustomerResource) => {
    const next = new URLSearchParams(location.search)
    next.set('resourceKind', resource.kind); next.set('resourceId', resource.id); next.delete('releaseId')
    if (resource.deploymentId) next.set('deploymentId', resource.deploymentId)
    next.delete('editor'); next.delete('editorSection')
    navigate({ pathname: location.pathname, search: next.toString() })
  }
  const close = () => {
    const next = new URLSearchParams(location.search)
    next.delete('resourceKind'); next.delete('resourceId'); next.delete('releaseId'); next.delete('editorSection')
    navigate({ pathname: location.pathname, search: next.toString() })
  }
  const title = props.mode === 'home' ? 'Your workspace' : props.mode === 'apps' ? 'Engage apps' : 'Deployments'
  const openConfiguration = (section: CustomerEditorSection = 'configuration') => {
    if (selected?.kind === 'deployment' && !offer) {
      navigate({ pathname: '/revisions', search: new URLSearchParams({ deploymentId: selected.id,
        ...(selectedRelease?.targetProfileId ? { targetProfileId: selectedRelease.targetProfileId } : {}),
      }).toString() })
      return
    }
    const next = new URLSearchParams(location.search); next.set('editor', 'configuration')
    next.set('editorSection', section)
    if (selected?.deploymentId) next.set('deploymentId', selected.deploymentId)
    navigate({ pathname: location.pathname, search: next.toString() })
  }
  const closeConfiguration = () => {
    const next = new URLSearchParams(location.search); next.delete('editor'); next.delete('editorSection')
    navigate({ pathname: location.pathname, search: next.toString() })
  }
  const cards = props.mode === 'home' ? allResources : props.mode === 'apps' ? appCards : deploymentCards
  const filtered = cards.filter(item => item.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()))
  const setupPath = props.mode === 'apps' ? '/ai-workspaces?view=manage' : '/deployments?view=setup&step=template'
  const resourceNotices = selected ? <Stack spacing={1}>
    {selected.issues.map(issue => <Alert key={issue.code} severity={issue.severity}>{issue.message}</Alert>)}
    {props.releaseError ? <Alert severity="error">Release details could not be loaded: {props.releaseError}</Alert> : null}
    {visibleReleases.flatMap(release => releaseIssues(release).map(issue => <Alert key={`${release.id}:${issue.code}`} severity={issue.severity}>{releaseEnvironment(release, offer)}: {issue.message}</Alert>))}
    {selectedReleaseId && !props.releaseLoading && !props.releaseError && !selectedRelease ? <Alert severity="error">The requested release is not available for this resource. No alternative release was selected.</Alert> : null}
  </Stack> : null
  const inspector = selected ? <Box component="aside" aria-label={`${selected.name} details`} sx={{ width: compact ? '100%' : 425, minWidth: 0, bgcolor: 'background.paper', height: '100%', overflowY: 'auto' }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ px: 3, pt: 2, pb: 1 }}>
      <Typography variant="overline" color="text.secondary">{selected.kind === 'app' ? 'Engage app' : 'Deployment'}</Typography>
      <Tooltip title="Close details"><IconButton aria-label="Close resource details" onClick={close}><CloseRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Stack spacing={2.5} sx={{ p: 3, pt: 1 }}>
      <Box><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>{selected.name}</Typography><Stack direction="row" gap={1} alignItems="center" sx={{ mt: 1 }}><Chip size="small" color={selected.severity} label={selected.status || 'Unknown'} /><Typography color="text.secondary" variant="caption" sx={{ overflowWrap: 'anywhere' }}>{selected.subtitle}</Typography></Stack></Box>
      {resourceNotices}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button variant="contained" onClick={() => openConfiguration()}>Configure</Button>
        {selected.kind === 'deployment' ? <Button component={RouterLink} variant="outlined" to={`/revisions?${new URLSearchParams({
          deploymentId: selected.id, ...(selectedRelease?.targetProfileId ? { targetProfileId: selectedRelease.targetProfileId } : {}),
        })}`}>Versions & updates</Button> : null}
        {selected.kind === 'app' && safeCustomerAppUrl(props.apps.find(item => item.installationId === selected.id)?.appUrl ?? null) ? <Button component="a" href={safeCustomerAppUrl(props.apps.find(item => item.installationId === selected.id)?.appUrl ?? null)!} target="_blank" rel="noopener noreferrer" startIcon={<OpenInNewRounded />}>Open app</Button> : null}
        {selected.deploymentId && deskRelease?.targetProfileId ? <CustomerDeskLink target={{ deploymentId: selected.deploymentId,
          targetProfileId: deskRelease.targetProfileId, releaseId: deskRelease.id }} /> : null}
      </Stack>
      <CustomerDetailSections key={`${selected.kind}:${selected.id}`} preferenceKey={selected.kind}
        overview={<Stack spacing={2.5}>
      <ResourceFacts facts={selected.facts} />
      {selected.kind === 'deployment' && offer && !props.releaseLoading && !props.releaseError ? <CustomerProductTasks
        deploymentId={selected.id} offer={offer} releases={props.releases} selectedReleaseId={selectedReleaseId}
        onSelectRelease={releaseId => { const next = new URLSearchParams(location.search); if (releaseId) next.set('releaseId', releaseId); else next.delete('releaseId'); navigate({ pathname: location.pathname, search: next.toString() }) }} /> : null}
      <Box><Typography fontWeight={600} sx={{ mb: 1.5 }}>Target releases</Typography>
        {props.releaseLoading ? <CircularProgress size={20} aria-label="Loading target releases" /> : visibleReleases.length ? <Stack spacing={1.5}>{visibleReleases.map(release => <Box key={release.id} sx={{ borderLeft: 2, borderColor: 'divider', pl: 1.5 }}>
          <Typography variant="body2" fontWeight={600}>{releaseEnvironment(release, offer)}</Typography><Typography variant="caption" color="text.secondary">{release.status} / {release.verificationStatus || 'Unknown verification'}</Typography>
          <Typography variant="caption" display="block" color="text.secondary">{release.updatedAt ? new Date(release.updatedAt).toLocaleString() : 'No observation time'}</Typography>
        </Box>)}</Stack> : !props.releaseError ? <Typography variant="body2" color="text.secondary">No target release is recorded.</Typography> : null}
      </Box>
      {selected.kind === 'deployment' ? <Box><Typography fontWeight={600} sx={{ mb: 1.5 }}>Connected Engage apps</Typography><Stack spacing={1}>{appCards.filter(app => app.deploymentId === selected.id).map(app => <Button key={app.id} variant="outlined" onClick={() => open(app)} endIcon={<ArrowForwardRounded />}>{app.name}</Button>)}</Stack></Box> : null}
        </Stack>}
        configuration={<Stack spacing={2.5}><ResourceFacts facts={selected.configurationFacts} /><Button variant="outlined" onClick={() => openConfiguration()}>Edit configuration</Button></Stack>}
        advanced={<Stack spacing={2.5}><ResourceFacts facts={selected.advancedFacts} />
          {selected.kind === 'deployment' ? <CustomerDeploymentAdvancedLinks deploymentId={selected.id} targetProfileId={selectedRelease?.targetProfileId} /> : null}
          <Box><Typography fontWeight={600}>Release references</Typography>{visibleReleases.map(release => <Typography key={release.id} variant="caption" color="text.secondary" display="block" sx={{ mt: 1, overflowWrap: 'anywhere' }}>{releaseEnvironment(release, offer)} / {release.id}</Typography>)}</Box>
          <Button variant="outlined" onClick={() => openConfiguration('advanced')}>Open advanced settings</Button>
        </Stack>} />
    </Stack>
  </Box> : null
  return <Box sx={{ display: 'flex', m: { xs: -2, md: -3.5 }, minHeight: 'calc(100vh - 62px)' }}>
    <Box sx={{ p: { xs: 2, md: 3.5 }, flex: 1, minWidth: 0 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
        <Box><Typography variant="h4">{title}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{props.mode === 'home' ? 'Your AI products and apps.' : props.mode === 'apps' ? 'Your published experiences and their current assignments.' : 'Your deployment configurations and verified target releases.'}</Typography></Box>
        {props.mode !== 'home' ? <Button component={RouterLink} to={setupPath} variant="contained" startIcon={<AddRounded />} sx={{ alignSelf: 'flex-start', whiteSpace: 'nowrap' }}>{props.mode === 'apps' ? 'Create Engage app' : 'Create deployment'}</Button> : null}
      </Stack>
      <Stack spacing={1} sx={{ mb: 2 }}>{props.errors.map((error,index) => <Alert key={index} severity="error">{error}</Alert>)}{unresolved ? <Alert severity="error" action={<Button onClick={close}>Close</Button>}>The requested resource is not available in this account. No other resource was selected.</Alert> : null}</Stack>
      {props.loading ? <CircularProgress aria-label="Loading customer resources" /> : null}
      {props.mode === 'home' ? !props.loading ? <CustomerProductHome offers={props.offers} deployments={props.deployments} apps={props.apps} errors={props.errors} onInspect={open} /> : null : !props.loading && !props.deployments.length && !props.apps.length && !props.errors.length ? <Box>
        <Typography variant="h6" sx={{ mb: 2 }}>Choose a starting template</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 2 }}>{props.offers.map(item => <Card key={item.id}><CardContent><Typography fontWeight={600}>{item.name}</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>{item.targets.length} reviewed targets</Typography><Button component={RouterLink} to={`/deployments?view=setup&step=template&offerId=${encodeURIComponent(item.id)}`} sx={{ mt: 2 }}>Open setup</Button></CardContent></Card>)}</Box>
        {!props.offers.length ? <Alert severity="info">No entitled template is currently available. Review your invitations in deployment setup.</Alert> : null}
      </Box> : <>
        <TextField size="small" placeholder="Find a resource..." inputProps={{ 'aria-label': 'Find a customer resource' }} value={search} onChange={event => setSearch(event.target.value)} sx={{ mb: 2.5, maxWidth: 300 }} />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: selected ? '1fr' : 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>
          {filtered.map(resource => <Card key={`${resource.kind}:${resource.id}`} sx={{ borderColor: selected?.id === resource.id ? 'primary.main' : 'divider' }}><CardActionArea onClick={() => open(resource)}>
            <CardContent><Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}>
              {resource.kind === 'app' ? <WidgetsOutlined color="primary" /> : <DnsOutlined color="primary" />}<Chip size="small" color={resource.severity} label={resource.status || 'Unknown'} />
            </Stack><Typography fontWeight={600} sx={{ mt: 2, overflowWrap: 'anywhere' }}>{resource.name}</Typography><Typography variant="caption" color="text.secondary">{resource.subtitle}</Typography>
              {resource.issues[0] ? <Typography color="warning.main" variant="caption" display="block" sx={{ mt: 1.5 }}>{resource.issues[0].message}</Typography> : null}
              <Divider sx={{ my: 1.5 }} /><Typography color="text.secondary" variant="caption">{resource.kind === 'app' ? 'Engage installation' : 'Deployment configuration'}</Typography>
            </CardContent>
          </CardActionArea></Card>)}
        </Box>
      </>}
      {props.dataObservedAt ? <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3 }}>Resource data loaded {new Date(props.dataObservedAt).toLocaleTimeString()}. This is not a runtime health probe.</Typography> : null}
    </Box>
    {compact ? <Drawer anchor="right" open={Boolean(selected)} onClose={close} PaperProps={{ sx: { width: '100%' } }}>{inspector}</Drawer> : selected ? <Box sx={{ width: 425, borderLeft: 1, borderColor: 'divider', flexShrink: 0, position: 'sticky', top: 62, height: 'calc(100vh - 62px)' }}>{inspector}</Box> : null}
    <CustomerConfigurationPanel open={Boolean(selected && props.configuration && params.get('editor') === 'configuration')} scopeKey={selected ? `${selected.kind}:${selected.id}` : ''} title={selected ? `${selected.name} / Configuration` : 'Configuration'} initialSection={params.get('editorSection') === 'advanced' ? 'advanced' : 'configuration'} notices={resourceNotices ? <Box sx={{ mb: 2 }}>{resourceNotices}</Box> : null} onClose={closeConfiguration}>{props.configuration}</CustomerConfigurationPanel>
  </Box>
}
