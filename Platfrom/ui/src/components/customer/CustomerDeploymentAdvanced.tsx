import { deploymentTerm } from '../../deploymentTerminology'
import type { ReactNode } from 'react'
import { Box, Breadcrumbs, Button, Chip, Link, Stack, Typography } from '@mui/material'
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined'
import MemoryOutlined from '@mui/icons-material/MemoryOutlined'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import BoltOutlined from '@mui/icons-material/BoltOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import HubOutlined from '@mui/icons-material/HubOutlined'
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useDeploymentWorkspace } from '../../workspace/DeploymentWorkspaceContext'

const editors = [
  ['/revisions', 'Versions & updates', HistoryOutlined],
  ['/actions', 'Actions', BoltOutlined],
  ['/prompts', 'AI behaviour', TuneOutlined],
  ['/providers', 'Providers', HubOutlined],
  ['/plugins', 'Plugins', ExtensionOutlined],
] as const

export function deploymentEditorLinks(deploymentId: string, targetProfileId?: string | null) {
  const params = new URLSearchParams({ deploymentId })
  if (targetProfileId) params.set('targetProfileId', targetProfileId)
  return editors.map(([path, label, Icon]) => ({ path, label, Icon, href: `${path}?${params}` }))
}

export function CustomerDeploymentAdvancedLinks({ deploymentId, targetProfileId }: {
  deploymentId: string
  targetProfileId?: string | null
}) {
  const location = useLocation()
  return <Stack component="nav" aria-label={`${deploymentTerm.singular} advanced configuration`} direction="row" gap={0.5}
    sx={{ borderBottom: 1, borderColor: 'divider', overflowX: 'auto', maxWidth: '100%' }}>
    {deploymentEditorLinks(deploymentId, targetProfileId).map(({ path, label, Icon, href }) => <Button key={path}
      component={RouterLink} to={href} startIcon={<Icon sx={{ fontSize: '18px !important' }} />}
      variant="text" aria-current={location.pathname === path ? 'page' : undefined}
      sx={{ flexShrink: 0, borderRadius: 0, borderBottom: 2, px: 1.5, py: 1.5,
        borderColor: location.pathname === path ? 'primary.main' : 'transparent',
        color: location.pathname === path ? 'primary.main' : 'text.secondary',
        bgcolor: location.pathname === path ? 'action.selected' : 'transparent' }}>
      {label}
    </Button>)}
  </Stack>
}

export function CustomerDeploymentAdvanced({ children }: { children: ReactNode }) {
  const { selectedDeploymentId, selectedDeploymentSummary } = useDeploymentWorkspace()
  const location = useLocation()
  if (!selectedDeploymentId) return <Typography>Select a {deploymentTerm.lowercase} to open its configuration.</Typography>
  const overview = `/deployments?${new URLSearchParams({
    resourceKind: 'deployment', resourceId: selectedDeploymentId, deploymentId: selectedDeploymentId,
  })}`
  return <Stack spacing={3} sx={{ maxWidth: 1440, mx: 'auto', minWidth: 0 }}>
    <Box component="header">
      <Breadcrumbs aria-label="Configuration breadcrumb" sx={{ mb: 2, fontSize: 12 }}>
        <Link component={RouterLink} to="/deployments" underline="hover" color="text.secondary">{deploymentTerm.plural}</Link>
        <Link component={RouterLink} to={overview} underline="hover" color="text.secondary">{selectedDeploymentSummary?.name ?? deploymentTerm.singular}</Link>
        <Typography variant="caption" color="text.secondary">Configuration</Typography>
      </Breadcrumbs>
      <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" gap={2} sx={{ mb: 2.5 }}>
        <Stack direction="row" gap={1.5} alignItems="center" sx={{ minWidth: 0 }}>
          <Box sx={{ width: 42, height: 42, flexShrink: 0, display: 'grid', placeItems: 'center',
            color: 'primary.main', bgcolor: 'action.selected', border: 1, borderColor: 'divider', borderRadius: 1 }}><MemoryOutlined /></Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="h1" variant="h5" sx={{ fontSize: 26, lineHeight: 1.25, overflowWrap: 'anywhere' }}>{selectedDeploymentSummary?.name ?? deploymentTerm.singular}</Typography>
            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{ mt: 0.75 }}>
              <Typography variant="caption" color="text.secondary">{deploymentTerm.singular}</Typography>
              {selectedDeploymentSummary?.environment && <Chip label={selectedDeploymentSummary.environment} variant="outlined" />}
              {selectedDeploymentSummary?.activeVersion && <Chip label={`Live ${selectedDeploymentSummary.activeVersion}`} variant="outlined" color="success" />}
              {selectedDeploymentSummary?.status && <Typography variant="caption" color="text.secondary">{selectedDeploymentSummary.status.split('_').join(' ').toLowerCase()}</Typography>}
            </Stack>
          </Box>
        </Stack>
        <Button component={RouterLink} startIcon={<ArrowBackOutlined />} to={overview} variant="outlined" sx={{ alignSelf: { xs: 'flex-start', sm: 'center' }, flexShrink: 0 }}>Back to {deploymentTerm.lowercase}</Button>
      </Stack>
      <CustomerDeploymentAdvancedLinks deploymentId={selectedDeploymentId}
        targetProfileId={new URLSearchParams(location.search).get('targetProfileId')} />
    </Box>
    <Box key={selectedDeploymentId}>{children}</Box>
  </Stack>
}
