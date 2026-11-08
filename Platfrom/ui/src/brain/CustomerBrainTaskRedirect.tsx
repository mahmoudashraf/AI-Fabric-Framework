import { Alert, CircularProgress, List, ListItemButton, ListItemText, Stack, Typography } from '@mui/material'
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { scopedBrainTaskPath, scopedBrainTasks } from './brainNavigation'

export function CustomerBrainTaskRedirect() {
  const location = useLocation()
  const { deployments, deploymentsLoading } = useDeploymentWorkspace()
  const requested = new URLSearchParams(location.search).get('deploymentId')
  if (requested) return <Navigate replace to={scopedBrainTaskPath(requested, location.pathname, location.search)} />
  return <Stack spacing={2}>
    <Typography component="h1" variant="h5">Choose a Smart Brain</Typography>
    <Typography color="text.secondary">Open its {scopedBrainTasks[location.pathname] === 'action-reviews' ? 'action reviews' : 'workspace'} to continue.</Typography>
    {deploymentsLoading ? <CircularProgress size={24} /> : !deployments.length ? <Alert severity="info">No Smart Brains are available in your account.</Alert> :
      <List aria-label="Choose a Smart Brain">{deployments.map(brain => <ListItemButton key={brain.id} component={RouterLink}
        to={scopedBrainTaskPath(brain.id, location.pathname, location.search)} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <ListItemText primary={brain.name} secondary={brain.status} />
      </ListItemButton>)}</List>}
  </Stack>
}
