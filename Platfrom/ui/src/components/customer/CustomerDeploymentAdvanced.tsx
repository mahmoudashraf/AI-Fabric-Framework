import type { ReactNode } from 'react'
import { Button, Stack, Typography } from '@mui/material'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import BoltOutlined from '@mui/icons-material/BoltOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom'
import { useDeploymentWorkspace } from '../../workspace/DeploymentWorkspaceContext'
import { brainPath, legacyBrainSections } from '../../brain/brainNavigation'

const editors = [
  ['knowledge', 'Knowledge', LayersOutlined], ['capabilities', 'AI capabilities', BoltOutlined],
  ['responses', 'Responses', TuneOutlined], ['updates', 'Updates', HistoryOutlined],
] as const

export function deploymentEditorLinks(deploymentId: string, targetProfileId?: string | null) {
  return editors.map(([path, label, Icon]) => ({ path, label, Icon, href: brainPath(deploymentId, path, targetProfileId) }))
}

export function CustomerDeploymentAdvancedLinks({ deploymentId, targetProfileId }: { deploymentId: string; targetProfileId?: string | null }) {
  return <Stack direction="row" gap={0.75} flexWrap="wrap" component="nav" aria-label="Smart Brain configuration">
    {deploymentEditorLinks(deploymentId, targetProfileId).map(({ path, label, Icon, href }) => <Button key={path} component={RouterLink} to={href} startIcon={<Icon sx={{ fontSize: 17 }} />}>{label}</Button>)}
  </Stack>
}

// Old bookmarks resolve to one canonical editor, never a second configuration implementation.
export function CustomerDeploymentAdvanced({ children: _children }: { children: ReactNode }) {
  const { requestedDeploymentId } = useDeploymentWorkspace()
  const location = useLocation()
  if (!requestedDeploymentId) return <Typography>Select a Smart Brain to open its configuration.</Typography>
  return <Navigate replace to={brainPath(requestedDeploymentId, legacyBrainSections[location.pathname] ?? '', new URLSearchParams(location.search).get('targetProfileId'))} />
}
