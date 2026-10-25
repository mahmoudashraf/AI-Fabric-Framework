import type { ReactNode } from 'react'
import { Box, Button, Stack, Typography } from '@mui/material'
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { useDeploymentWorkspace } from '../../workspace/DeploymentWorkspaceContext'

const editors = [
  ['/revisions', 'Versions & updates'],
  ['/actions', 'Actions'],
  ['/prompts', 'Prompts'],
  ['/providers', 'Providers'],
  ['/plugins', 'Plugins'],
] as const

export function CustomerDeploymentAdvancedLinks({ deploymentId, targetProfileId }: {
  deploymentId: string
  targetProfileId?: string | null
}) {
  const location = useLocation()
  const params = new URLSearchParams({ deploymentId })
  if (targetProfileId) params.set('targetProfileId', targetProfileId)
  return <Stack component="nav" aria-label="Deployment advanced configuration" direction="row" gap={1} flexWrap="wrap">
    {editors.map(([path, label]) => <Button key={path} component={RouterLink} to={`${path}?${params}`}
      variant={location.pathname === path ? 'contained' : 'outlined'} aria-current={location.pathname === path ? 'page' : undefined}>
      {label}
    </Button>)}
  </Stack>
}

export function CustomerDeploymentAdvanced({ children }: { children: ReactNode }) {
  const { selectedDeploymentId, selectedDeploymentSummary } = useDeploymentWorkspace()
  const location = useLocation()
  if (!selectedDeploymentId) return <Typography>Select a deployment to open its configuration.</Typography>
  return <Stack spacing={3}>
    <Box>
      <Button component={RouterLink} startIcon={<ArrowBackOutlined />} to={`/deployments?${new URLSearchParams({
        resourceKind: 'deployment', resourceId: selectedDeploymentId, deploymentId: selectedDeploymentId,
      })}`}>Back to deployment</Button>
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>{selectedDeploymentSummary?.name ?? 'Deployment'} / Advanced</Typography>
      <CustomerDeploymentAdvancedLinks deploymentId={selectedDeploymentId}
        targetProfileId={new URLSearchParams(location.search).get('targetProfileId')} />
    </Box>
    <Box key={selectedDeploymentId}>{children}</Box>
  </Stack>
}
