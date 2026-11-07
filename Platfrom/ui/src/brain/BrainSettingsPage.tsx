import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import CodeRounded from '@mui/icons-material/CodeRounded'
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import { Alert, Box, Button, Link, List, ListItemButton, ListItemIcon, ListItemText, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { ProvidersPage, type ProviderSettingsView } from '../pages/ProvidersPage'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { useBrainWorkspace } from './BrainWorkspaceContext'

const settings = [
  { path: 'models', label: 'AI models', description: 'Primary models, orchestration, answer generation and provider fallback.', icon: AutoAwesomeOutlined },
  { path: 'storage', label: 'Storage & search', description: 'Embedding models, vector backends and storage scope.', icon: StorageOutlined },
  { path: 'access', label: 'Access', description: 'Capability sign-in requirements, anonymous access and trusted routes.', icon: ShieldOutlined },
  { path: 'delivery', label: 'Event delivery', description: 'Webhook destinations, post-action policies and delivery status.', icon: NotificationsActiveOutlined },
  { path: 'technical-files', label: 'Technical files', description: 'Runtime wiring, provider JSON, recovery policy and diagnostic checks.', icon: CodeRounded },
] as const

export function BrainSettingsPage({ section }: { section?: string } = {}) {
  const { brainId, href } = useBrainWorkspace()
  const location = useLocation()
  const selected = section ?? location.pathname.split('/')[4] ?? ''
  const item = settings.find(setting => setting.path === selected)
  const providerView: ProviderSettingsView | null = selected === 'models' || selected === 'storage' || selected === 'technical-files'
    ? selected : null

  return <Box sx={{ ...brainPaneSx, flex: 1 }}>
    {selected && <Button component={RouterLink} to={href('settings')} startIcon={<ArrowBackRounded />}
      size="small" sx={{ mb: 2 }}>Technical settings</Button>}
    <BrainPageHeader title={item?.label ?? 'Technical settings'} />
    {!selected ? <List disablePadding aria-label="Technical settings">
      {settings.map(({ path, label, description, icon: Icon }) => <ListItemButton key={path} component={RouterLink}
        to={href(`settings/${path}`)} sx={{ px: 0, py: 2, gap: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <ListItemIcon sx={{ minWidth: 28, color: 'text.secondary' }}><Icon /></ListItemIcon>
        <ListItemText primary={label} secondary={description} primaryTypographyProps={{ fontWeight: 600, fontSize: 15 }}
          secondaryTypographyProps={{ fontSize: 13, sx: { mt: 0.5, overflowWrap: 'anywhere' } }} />
        <ArrowForwardRounded fontSize="small" color="action" />
      </ListItemButton>)}
    </List> : providerView ? <Stack spacing={2.5}>
      {selected === 'models' && <Link component={RouterLink} to={href('settings/technical-files')}
        sx={{ alignSelf: 'flex-start', fontSize: 13 }}>Advanced provider settings</Link>}
      {selected === 'technical-files' && <Stack direction="row" gap={2} flexWrap="wrap" sx={{ pb: 1, borderBottom: 1, borderColor: 'divider' }}>
        <Link component={RouterLink} to={href('capabilities/actions')}>Action JSON &amp; routes</Link>
        <Link component={RouterLink} to={href('responses/advanced')}>Prompt technical details</Link>
        <Link component={RouterLink} to={href('updates')}>Versions &amp; release records</Link>
      </Stack>}
      <ProvidersPage key={`${brainId}:${providerView}`} brainId={brainId} configurationOnly view={providerView} />
    </Stack> : selected === 'access' || selected === 'delivery' ? <Stack spacing={2} sx={{ maxWidth: 720 }}>
      <Typography variant="body2" color="text.secondary">
        {selected === 'access'
          ? 'Capability access remains attached to its action definition and trusted HTTP route. Global staff administration is separate.'
          : 'Webhook targets and post-action policies remain attached to the capability configuration. Live delivery status is separate from saved draft settings.'}
      </Typography>
      <Button component={RouterLink} to={href('capabilities/actions')} endIcon={<ArrowForwardRounded />}
        variant="outlined" sx={{ alignSelf: 'flex-start', whiteSpace: 'normal', textAlign: 'left' }}>
        {selected === 'access' ? 'Open capability access & routes' : 'Open webhook policies & deliveries'}
      </Button>
    </Stack> : <Alert severity="warning">This settings section is not available.</Alert>}
  </Box>
}
