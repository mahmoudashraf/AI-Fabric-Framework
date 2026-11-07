import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Chip, Divider, List, ListItemButton, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

export function BrainOverviewPage() {
  const { workspace, selectedDeploymentSummary } = useDeploymentWorkspace()
  const { release, targetProfile, href } = useBrainWorkspace()
  const sections = [
    { name: 'Knowledge', description: 'Approved documents and live information', path: 'knowledge', Icon: LayersOutlined, state: workspace?.documentKnowledgeConfigured || workspace?.externalIntegrationConfigured ? 'Configured' : 'Add information' },
    { name: 'AI capabilities', description: 'Useful tasks, services and customer requests', path: 'capabilities', Icon: AutoAwesomeOutlined, state: 'Review capabilities' },
    { name: 'Responses', description: 'Tone, guidance and helpful next steps', path: 'responses', Icon: ChatBubbleOutlineRounded, state: 'Personalize' },
    { name: 'Channels', description: 'Website workspace and standalone Engage apps', path: 'channels', Icon: AccountTreeOutlined, state: 'Connect Engage' },
    { name: 'Human support', description: 'Your team, conversation handover and action reviews', path: 'human-support', Icon: HeadsetMicOutlined, state: workspace?.conversationHandoverConfigured ? 'Configured' : 'Optional' },
  ]
  return <>
    <BrainPageHeader title="Your AI, connected to your business" description={selectedDeploymentSummary?.name}
      action={<Button component={RouterLink} to={href(release ? 'try' : 'updates')} variant="contained" endIcon={<ArrowForwardRounded />}>{release ? 'Try your AI' : 'Review setup'}</Button>} />
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0, gap: 3, flexDirection: { xs: 'column', lg: 'row' } }}>
      <Box sx={{ ...brainPaneSx, flex: 1 }}>
        {!release && <Alert severity="info" sx={{ mb: 2 }}>Your configuration can be prepared without running infrastructure. Publish and apply it when you are ready.</Alert>}
        {release?.errorMessage && <Alert severity="error" sx={{ mb: 2 }}>{release.errorMessage}</Alert>}
        <List disablePadding aria-label="Smart Brain outcomes">{sections.map(item => <ListItemButton key={item.path} component={RouterLink} to={href(item.path)} sx={{ py: 2, gap: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <ListItemIcon sx={{ minWidth: 28 }}><item.Icon color="primary" /></ListItemIcon>
          <ListItemText primary={item.name} secondary={item.description} primaryTypographyProps={{ fontWeight: 600, fontSize: 14 }} secondaryTypographyProps={{ fontSize: 12 }} />
          <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', sm: 'block' } }}>{item.state}</Typography><ArrowForwardRounded sx={{ fontSize: 17, color: 'text.secondary' }} />
        </ListItemButton>)}</List>
      </Box>
      <Box component="aside" sx={{ width: { lg: 285 }, borderLeft: { lg: 1 }, borderColor: 'divider', pl: { lg: 3 } }}>
        <Typography variant="subtitle1">This environment</Typography>
        <Stack gap={1.5} sx={{ mt: 2 }}><Typography variant="body2">{targetProfile?.environmentName ?? 'Choose an environment'}</Typography>
          <Chip label={release ? release.status.split('_').join(' ').toLowerCase() : 'Not deployed'} variant="outlined" sx={{ alignSelf: 'flex-start' }} />
          <Typography variant="body2" color="text.secondary">{release ? 'This is the applied release. Saved draft changes do not update it automatically.' : 'No applied release is selected.'}</Typography></Stack>
        <Divider sx={{ my: 2.5 }} /><Typography variant="subtitle2">Saved configuration</Typography>
        <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">{workspace?.draft ? `Revision ${workspace.draft.revisionNumber}. Shared across your environments.` : 'Loading saved draft...'}</Typography>
        <Button component={RouterLink} to={href('updates')} sx={{ mt: 2 }}>Review changes & history</Button>
      </Box>
    </Box>
  </>
}
