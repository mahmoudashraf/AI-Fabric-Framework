import { Link as RouterLink } from 'react-router-dom'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Divider, Stack, Typography } from '@mui/material'
import OpenInNewOutlined from '@mui/icons-material/OpenInNewOutlined'
import WidgetsOutlined from '@mui/icons-material/WidgetsOutlined'
import LanguageOutlined from '@mui/icons-material/LanguageOutlined'
import CodeOutlined from '@mui/icons-material/CodeOutlined'
import { fetchAIWorkspaceInstallations } from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { CustomerDeskActivation } from '../pages/CustomerKnowledgeConnectionsPage'
import { IntegrationsPage } from '../pages/IntegrationsPage'
import { safeCustomerAppUrl } from '../components/customer/customerResourceModels'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainWorkspace, useBrainEditor } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

export function useBrainApps() {
  const { session } = usePlatformAuth()
  const { selectedDeploymentSummary } = useDeploymentWorkspace()
  const { brainId } = useBrainWorkspace()
  const customerId = session?.customerId ?? selectedDeploymentSummary?.binding?.customerId ?? ''
  const query = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId), enabled: Boolean(customerId), retry: false })
  return { ...query, apps: (query.data ?? []).filter(app => app.deploymentId === brainId), customerId }
}

export function BrainChannelsPage() {
  const { session } = usePlatformAuth()
  const { brainId, target, href } = useBrainWorkspace()
  const { apps, isPending, isError, error, customerId } = useBrainApps()
  return <><BrainPageHeader title="Where customers meet your AI" description="A branded website workspace, a standalone app, or both."
    action={<Button component={RouterLink} to={`/ai-workspaces?${new URLSearchParams({ deploymentId: brainId, ...(customerId ? { customerId } : {}) })}`} variant="contained" startIcon={<WidgetsOutlined />}>Manage Engage apps</Button>} />
    <Box sx={brainPaneSx}>
      {isPending && customerId && <CircularProgress size={24} aria-label="Loading Engage apps" />}
      {isError && <Alert severity="error">{error.message}</Alert>}
      {!customerId && <Alert severity="info">Assign this Smart Brain to a customer account before connecting its Engage apps.</Alert>}
      {!isPending && !apps.length && <Alert severity="info">No Engage app is connected yet. Create one in Engage apps, then connect it to this Smart Brain.</Alert>}
      {apps.map(app => {
        const url = safeCustomerAppUrl(app.appUrl)
        const editor = `/ai-workspaces?${new URLSearchParams(session?.role === 'CUSTOMER_ADMIN'
          ? { resourceKind: 'app', resourceId: app.installationId, deploymentId: brainId }
          : { customerId, installationId: app.installationId })}`
        const issues = app.readinessChecks.filter(check => check.status !== 'PASSED')
        return <Box component="section" key={app.installationId} sx={{ py: 2.5, borderBottom: 1, borderColor: 'divider' }}>
          <Stack direction="row" gap={2} alignItems="center" justifyContent="space-between" flexWrap="wrap">
            <Stack direction="row" gap={1.5} alignItems="center">{app.deliveryMode === 'embedded' ? <CodeOutlined color="primary" /> : <LanguageOutlined color="primary" />}<Box>
              <Typography variant="h6">{app.displayName}</Typography><Typography variant="caption" color="text.secondary">{app.deliveryMode === 'embedded' ? 'Website embed & Max workspace' : 'Standalone AI workspace'}</Typography></Box></Stack>
            <Chip label={app.status} color={app.status === 'ACTIVE' && app.ready ? 'success' : 'default'} variant="outlined" />
          </Stack>
          <Stack direction="row" gap={2} flexWrap="wrap" sx={{ mt: 2 }}><Typography variant="body2">{app.interactionPolicy === 'public' ? 'Anonymous access' : app.interactionPolicy === 'login-required' ? 'Sign-in required' : 'Sign-in for protected capabilities'}</Typography>
            {target && app.releaseId !== target.releaseId && <Typography variant="body2" color="warning.main">Assigned to a different release</Typography>}</Stack>
          {issues.map((check, index) => <Alert severity="warning" key={index} sx={{ mt: 1 }}>{check.message}</Alert>)}
          <Stack direction="row" gap={1.25} sx={{ mt: 2 }} flexWrap="wrap"><Button component={RouterLink} to={editor} variant="outlined">Branding & preview</Button>
            <Button component={RouterLink} to={`${editor}&editor=configuration`}>{app.deliveryMode === 'embedded' ? 'Installation & allowed websites' : 'Address & custom domains'}</Button>
            {url && app.status === 'ACTIVE' && <Button component="a" href={url} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewOutlined />}>Open app</Button>}</Stack>
          {app.domains.length > 0 && <Typography variant="caption" sx={{ mt: 1.5, display: 'block' }} color="text.secondary">{app.domains.map(domain => `${domain.hostname}: ${domain.status}`).join(' / ')}</Typography>}
        </Box>
      })}
      <Divider sx={{ my: 2.5 }} /><Button component={RouterLink} to={href('try')}>Try the connected experience</Button>
    </Box></>
}

export function BrainHumanSupportPage() {
  const { session } = usePlatformAuth()
  const { target, release, href } = useBrainWorkspace()
  const [deskBusy, setDeskBusy] = useState(false)
  const [channelBusy, setChannelBusy] = useState(false)
  useBrainEditor({ dirty: false, busy: deskBusy || channelBusy, message: 'Human-support operations use this environment; agent permissions remain separate.' })
  return <><BrainPageHeader title="Bring your team into the conversation" description="Connect human conversations and separately authorized action reviews." />
    <Box sx={brainPaneSx}>{!target ? <Alert severity="info">Apply a release to this environment before connecting human support. <Button component={RouterLink} to={href('updates')}>Review setup</Button></Alert> : <>
      {session?.role === 'CUSTOMER_ADMIN' ? <CustomerDeskActivation target={target} onReady={() => {}} onBusyChange={setDeskBusy} />
        : <Alert severity="info" sx={{ mb: 2 }}>The owning customer can enable and manage LoomAI Desk from this page. Staff can inspect the conversation channel below.</Alert>}
      <IntegrationsPage initialReleaseId={release!.id} handoverOnly lockReleaseSelection workspaceView onBusyChange={setChannelBusy} />
    </>}</Box></>
}
