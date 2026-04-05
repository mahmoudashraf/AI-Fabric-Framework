import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from '@mui/material'
import { Link, useLocation } from 'react-router-dom'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function buildDeploymentTarget(pathname: string, search: string, deploymentId: string): string {
  const params = new URLSearchParams(search)
  params.set('deploymentId', deploymentId)
  const nextSearch = params.toString()
  return nextSearch.length > 0 ? `${pathname}?${nextSearch}` : pathname
}

export function DeploymentWorkspaceUnresolvedState() {
  const location = useLocation()
  const { deployments, unresolvedRequestedDeploymentId } = useDeploymentWorkspace()

  if (!unresolvedRequestedDeploymentId) {
    return null
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Deployment Not Found" color="error" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          The requested deployment is not available
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          The URL requested deployment <strong>{unresolvedRequestedDeploymentId}</strong>, but it does not exist in
          the current workspace or you no longer have access to it.
        </Typography>
      </Box>

      <Alert severity="error">
        Use one of the deployments below to reopen this page, or go back to the deployments grid to pick a different
        target.
      </Alert>

      <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
        <Button component={Link} to="/deployments" variant="contained" color="secondary">
          Open deployments grid
        </Button>
      </Stack>

      {deployments.length > 0 ? (
        <Grid container spacing={2}>
          {deployments.map((deployment) => (
            <Grid item xs={12} md={6} xl={4} key={deployment.id}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Box>
                      <Typography variant="h6" sx={{ fontWeight: 700 }}>
                        {deployment.name}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {deployment.id}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={deployment.environment} variant="outlined" />
                      <Chip label={deployment.status} color="primary" />
                    </Stack>
                    <Button
                      component={Link}
                      to={buildDeploymentTarget(location.pathname, location.search, deployment.id)}
                      variant="outlined"
                    >
                      Open this deployment
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      ) : (
        <Alert severity="info">
          No deployments are currently available in this workspace. Create one from the deployments grid first.
        </Alert>
      )}
    </Stack>
  )
}
