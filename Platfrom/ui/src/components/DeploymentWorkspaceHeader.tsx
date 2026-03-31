import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { Link, useLocation } from 'react-router-dom'
import { DEPLOYMENT_WORKSPACE_PATHS, useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const sectionLabels: Record<(typeof DEPLOYMENT_WORKSPACE_PATHS)[number], string> = {
  '/actions': 'Actions',
  '/approvals': 'Approvals',
  '/access': 'Access',
  '/knowledge': 'Knowledge',
  '/poc': 'POC',
  '/prompts': 'Prompts',
  '/providers': 'Providers',
  '/security': 'Security',
  '/verification': 'Verification',
  '/revisions': 'Versions',
  '/diagnostics': 'Diagnostics',
}

export function DeploymentWorkspaceHeader() {
  const location = useLocation()
  const {
    deployments,
    deploymentsLoading,
    isScopedPage,
    selectedDeploymentId,
    selectedDeploymentSummary,
    workspace,
    workspaceLoading,
    setSelectedDeploymentId,
    buildWorkspacePath,
  } = useDeploymentWorkspace()

  if (!isScopedPage) {
    return null
  }

  return (
    <Box sx={{ px: 3.5, pt: 2.5, pb: 0 }}>
      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Stack
              direction={{ xs: 'column', lg: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', lg: 'center' }}
            >
              <Box>
                <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: 1.2 }}>
                  Deployment Workspace
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: -0.4 }}>
                  {workspace?.deployment.name ?? selectedDeploymentSummary?.name ?? 'Select a deployment'}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>
                  Keep one deployment selected while you move across configuration, versions, verification, and diagnostics.
                </Typography>
              </Box>

              <TextField
                select
                size="small"
                label="Deployment"
                value={selectedDeploymentId}
                onChange={(event) => setSelectedDeploymentId(event.target.value)}
                disabled={deploymentsLoading || deployments.length === 0}
                sx={{ minWidth: { xs: '100%', sm: 320 } }}
                SelectProps={{ native: true }}
              >
                <option value="">
                  {deploymentsLoading ? 'Loading deployments...' : 'Select deployment'}
                </option>
                {deployments.map((deployment) => (
                  <option key={deployment.id} value={deployment.id}>
                    {deployment.name} ({deployment.environment})
                  </option>
                ))}
              </TextField>
            </Stack>

            {!selectedDeploymentId ? (
              <Alert severity="info">
                Create a deployment from the deployments grid to start using the workspace.
              </Alert>
            ) : workspaceLoading ? (
              <Stack direction="row" spacing={1.5} alignItems="center">
                <CircularProgress size={18} />
                <Typography color="text.secondary">Loading workspace context...</Typography>
              </Stack>
            ) : workspace ? (
              <>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Environment: ${workspace.deployment.environment}`} variant="outlined" />
                  <Chip label={`Template: ${workspace.template.name}`} variant="outlined" />
                  <Chip label={workspace.deployment.status} color="primary" />
                  <Chip label={`Health: ${workspace.deployment.healthStatus}`} variant="outlined" />
                  <Chip label={`Draft r${workspace.draft.revisionNumber}`} variant="outlined" />
                  <Chip label={`Versions: ${workspace.versionCount}`} variant="outlined" />
                  <Chip label={`Releases: ${workspace.releaseCount}`} variant="outlined" />
                  <Chip label={`Verification runs: ${workspace.verificationRunCount}`} variant="outlined" />
                  {workspace.deployment.activeVersion ? (
                    <Chip label={`Active: ${workspace.deployment.activeVersion}`} variant="outlined" />
                  ) : null}
                  {workspace.latestRelease?.status ? (
                    <Chip label={`Latest release: ${workspace.latestRelease.status}`} color="secondary" />
                  ) : null}
                </Stack>

                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {DEPLOYMENT_WORKSPACE_PATHS.map((pathname) => {
                    const active = location.pathname === pathname
                    return (
                      <Button
                        key={pathname}
                        component={Link}
                        to={buildWorkspacePath(pathname)}
                        variant={active ? 'contained' : 'outlined'}
                        size="small"
                      >
                        {sectionLabels[pathname]}
                      </Button>
                    )
                  })}
                </Stack>
              </>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
