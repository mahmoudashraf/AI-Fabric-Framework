import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Chip, Divider, List, ListItemButton, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import { useQuery } from '@tanstack/react-query'
import { fetchDeploymentReleases } from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { useBrainApps } from './BrainExperiencePages'
import { brainOverviewState } from './brainOverview'

export function BrainOverviewPage() {
  const { workspace, workspaceLoading, selectedDeploymentSummary } = useDeploymentWorkspace()
  const { brainId, release, targetProfile, href } = useBrainWorkspace()
  const apps = useBrainApps()
  const releases = useQuery({ queryKey: ['deployment-releases', brainId], queryFn: () => fetchDeploymentReleases(brainId) })
  const latestRelease = (releases.data ?? []).filter(item => item.targetProfileId === targetProfile?.id)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0] ?? null
  const loading = workspaceLoading || releases.isPending || Boolean(apps.customerId && apps.isPending)
  const unavailable = releases.isError || apps.isError
  const state = brainOverviewState({ workspace, release, latestRelease, apps: apps.apps, loading, unavailable, hasTarget: Boolean(targetProfile) })
  const sections = [
    { name: 'Knowledge', description: 'Approved documents and live information', path: 'knowledge', Icon: LayersOutlined, state: !workspace ? 'Status unavailable' : workspace.documentKnowledgeConfigured || workspace.externalIntegrationConfigured ? 'Configured' : 'Add information' },
    { name: 'AI capabilities', description: 'Useful tasks, services and customer requests', path: 'capabilities', Icon: AutoAwesomeOutlined, state: 'Review capabilities' },
    { name: 'Responses', description: 'Tone, guidance and helpful next steps', path: 'responses', Icon: ChatBubbleOutlineRounded, state: 'Personalize' },
    { name: 'Channels', description: 'Website workspace and standalone Engage apps', path: 'channels', Icon: AccountTreeOutlined,
      state: !targetProfile ? 'Choose environment' : loading ? 'Checking apps' : apps.isError ? 'Status unavailable' : state.activeApps.length ? `${state.activeApps.length} active` : state.matchingApps.length ? 'Review app setup' : 'Connect app' },
    { name: 'Human support', description: 'Your team, conversation handover and action reviews', path: 'human-support', Icon: HeadsetMicOutlined, state: !workspace ? 'Status unavailable' : workspace.conversationHandoverConfigured ? 'Configured' : 'Optional' },
  ]
  return <>
    <BrainPageHeader title="Your AI, connected to your business" description={selectedDeploymentSummary?.name}
      action={<Button component={RouterLink} to={href(state.verified ? state.standaloneApps.length ? 'try' : 'channels' : 'updates')} variant="outlined" endIcon={<ArrowForwardRounded />} sx={{ flexShrink: 0 }}>{state.verified ? state.standaloneApps.length ? 'Try your AI' : 'View channels' : 'Review setup'}</Button>} />
    <Box sx={{ ...brainPaneSx, flex: 1 }}>
      {unavailable && <Alert severity="error" sx={{ mb: 2 }}>Some status could not be loaded. {releases.error?.message ?? apps.error?.message}<Button onClick={() => { void releases.refetch(); if (apps.customerId) void apps.refetch() }}>Retry</Button></Alert>}
      {!workspaceLoading && !workspace && <Alert severity="warning" sx={{ mb: 2 }}>Saved configuration could not be loaded. Reopen this Smart Brain to try again.</Alert>}
      {!targetProfile && <Alert severity="info" sx={{ mb: 2 }}>Choose an environment above to see its release and connected customer experience.</Alert>}
      {state.next && <Alert severity={state.next.warning ? 'warning' : 'info'} icon={state.activeApps.length && state.verified && !state.changes ? <CheckCircleOutlineRounded /> : undefined}
        sx={{ mb: 2, border: 1, borderColor: state.next.warning ? 'warning.main' : 'divider', borderRadius: 1, '& .MuiAlert-message': { width: '100%' } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} justifyContent="space-between" alignItems={{ sm: 'center' }}>
          <Box><Typography fontSize={14} fontWeight={600}>{state.next.title}</Typography><Typography fontSize={12} color="text.secondary" sx={{ mt: 0.5 }}>{state.next.description}</Typography></Box>
          <Button component={RouterLink} to={href(state.next.section)} variant="contained" endIcon={<ArrowForwardRounded />} sx={{ flexShrink: 0, alignSelf: { xs: 'flex-start', sm: 'center' } }}>{state.next.label}</Button>
        </Stack>
      </Alert>}
      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', lg: 'row' } }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Stack direction="row" justifyContent="space-between" gap={1} flexWrap="wrap" sx={{ pb: 0.5 }}><Typography variant="subtitle2">Shape your customer experience</Typography><Typography variant="caption" color="text.secondary">Saved configuration</Typography></Stack>
        <List disablePadding aria-label="Smart Brain outcomes">{sections.map(item => <ListItemButton key={item.path} component={RouterLink} to={href(item.path)} sx={{ py: 0.75, px: 0.5, gap: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <ListItemIcon sx={{ minWidth: 36, height: 36, justifyContent: 'center', alignItems: 'center', bgcolor: 'action.hover', borderRadius: 1 }}><item.Icon color="primary" sx={{ fontSize: 21 }} /></ListItemIcon>
          <ListItemText primary={item.name} secondary={item.description} primaryTypographyProps={{ fontWeight: 600, fontSize: 14, lineHeight: 1.35 }} secondaryTypographyProps={{ fontSize: 12, lineHeight: 1.4 }} />
          <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', sm: 'block' } }}>{item.state}</Typography><ArrowForwardRounded sx={{ fontSize: 17, color: 'text.secondary' }} />
        </ListItemButton>)}</List>
      </Box>
      <Box component="aside" aria-label="Environment summary" sx={{ width: { lg: 265 }, flexShrink: 0, borderLeft: { lg: 1 }, borderTop: { xs: 1, lg: 0 }, borderColor: 'divider', pl: { lg: 3 }, pt: { xs: 2, lg: 0 } }}>
        <Typography variant="subtitle1">This environment</Typography>
        <Stack gap={1} sx={{ mt: 1 }}><Typography variant="body2" sx={{ textTransform: 'capitalize' }}>{targetProfile?.environmentName ?? 'Choose an environment'}</Typography>
          <Chip label={releases.isError ? 'Status unavailable' : releases.isPending ? 'Checking release' : !targetProfile ? 'Choose environment' : state.verified ? 'Verified release' : release ? 'Verification required' : 'Not deployed'} color={state.verified && !releases.isError ? 'success' : 'default'} variant="outlined" sx={{ alignSelf: 'flex-start' }} />
          <Typography variant="body2" color="text.secondary">{release ? 'Draft edits do not change this release.' : 'No applied release is selected.'}</Typography></Stack>
        <Divider sx={{ my: 1.5 }} /><Typography variant="subtitle2">Saved configuration</Typography>
        <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">{workspace?.draft ? `Revision ${workspace.draft.revisionNumber}. Shared across your environments.` : workspaceLoading ? 'Loading saved draft...' : 'Saved draft unavailable.'}</Typography>
        <Divider sx={{ my: 1.5 }} /><Typography variant="subtitle2">Customer access</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{!targetProfile ? 'Select an environment to inspect its connected apps.' : loading ? 'Checking connected apps...' : apps.isError ? 'App status is unavailable.' : state.activeApps.length ? `${state.activeApps.length} active Engage ${state.activeApps.length === 1 ? 'app is' : 'apps are'} assigned to this release.` : 'No active, ready Engage app is assigned to this release.'}</Typography>
      </Box>
      </Box>
    </Box>
  </>
}
