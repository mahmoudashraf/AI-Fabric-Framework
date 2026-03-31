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
import type { DeploymentWorkspaceSummary } from '../api/platformApi'
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
  '/users': 'User Access',
}

function swaggerUiUrl(baseUrl: string | null | undefined): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}/swagger-ui/index.html`
}

function workspaceRoleGuidance(workspace: DeploymentWorkspaceSummary): {
  severity: 'info' | 'success'
  message: string
} {
  if (workspace.access.canAdmin) {
    return {
      severity: 'success',
      message: 'Admin access: you can configure the deployment, manage assignments, run releases, and perform guarded destructive actions.',
    }
  }
  if (workspace.access.canEdit) {
    return {
      severity: 'success',
      message: 'Editor access: you can edit drafts, publish releases, and operate the deployment, but assignment and destructive controls remain restricted.',
    }
  }
  if (workspace.access.canOperate) {
    return {
      severity: 'info',
      message: 'Operator access: you can apply published versions, run verification, and use the POC workspace, but draft configuration stays read-only.',
    }
  }
  return {
    severity: 'info',
    message: 'Viewer access: this deployment workspace is read-only. Use diagnostics, versions, and configuration pages to review state without changing it.',
  }
}

function workspacePrimaryAction(workspace: DeploymentWorkspaceSummary): { label: string; path: string } {
  const deploymentId = encodeURIComponent(workspace.deployment.id)
  const latestRelease = workspace.deployment.latestRelease
  if (latestRelease && ['APPLY_REQUESTED', 'PROVISIONING', 'VERIFYING'].includes(latestRelease.status)) {
    return { label: 'Track rollout', path: `/diagnostics?deploymentId=${deploymentId}` }
  }
  if (workspace.deployment.healthStatus === 'ATTENTION') {
    return { label: 'Review diagnostics', path: `/diagnostics?deploymentId=${deploymentId}` }
  }
  if (workspace.access.canEdit && (workspace.deployment.activeVersion == null || workspace.deployment.activeVersion === 'draft' || workspace.deployment.status === 'DRAFT')) {
    return { label: 'Continue configuration', path: `/actions?deploymentId=${deploymentId}` }
  }
  if (workspace.access.canOperate && workspace.latestRelease == null) {
    return { label: 'Prepare first release', path: `/revisions?deploymentId=${deploymentId}` }
  }
  if (workspace.access.canOperate) {
    return { label: 'Open POC workspace', path: `/poc?deploymentId=${deploymentId}` }
  }
  return { label: 'Open diagnostics', path: `/diagnostics?deploymentId=${deploymentId}` }
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

  const roleGuidance = workspace ? workspaceRoleGuidance(workspace) : null
  const primaryAction = workspace ? workspacePrimaryAction(workspace) : null
  const runtimeSwaggerUrl = swaggerUiUrl(workspace?.deployment.runtimeBaseUrl)
  const connectorSwaggerUrl = swaggerUiUrl(workspace?.deployment.connectorBaseUrl)

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
                  Keep one deployment selected while you move across configuration, access, versions, verification, and diagnostics.
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
                  <Chip label={`Role: ${workspace.access.assignmentRole}`} variant="outlined" />
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

                {roleGuidance ? (
                  <Alert severity={roleGuidance.severity}>
                    {roleGuidance.message}
                  </Alert>
                ) : null}

                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {primaryAction ? (
                    <Button
                      component={Link}
                      to={primaryAction.path}
                      variant="contained"
                      color="secondary"
                      size="small"
                    >
                      {primaryAction.label}
                    </Button>
                  ) : null}
                  {workspace.access.canOperate ? (
                    <Button component={Link} to={buildWorkspacePath('/poc')} variant="outlined" size="small">
                      POC Workspace
                    </Button>
                  ) : null}
                  {workspace.access.canEdit ? (
                    <Button component={Link} to={buildWorkspacePath('/prompts')} variant="outlined" size="small">
                      Prompt Editing
                    </Button>
                  ) : null}
                  <Button component={Link} to={buildWorkspacePath('/revisions')} variant="outlined" size="small">
                    Releases
                  </Button>
                  <Button component={Link} to={buildWorkspacePath('/diagnostics')} variant="outlined" size="small">
                    Diagnostics
                  </Button>
                  {workspace.access.canAdmin ? (
                    <Button component={Link} to={buildWorkspacePath('/access')} variant="outlined" size="small">
                      Access
                    </Button>
                  ) : null}
                  {workspace.deployment.runtimeBaseUrl ? (
                    <Button href={workspace.deployment.runtimeBaseUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Runtime
                    </Button>
                  ) : null}
                  {runtimeSwaggerUrl ? (
                    <Button href={runtimeSwaggerUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Runtime Swagger
                    </Button>
                  ) : null}
                  {workspace.deployment.connectorBaseUrl ? (
                    <Button href={workspace.deployment.connectorBaseUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Connector
                    </Button>
                  ) : null}
                  {connectorSwaggerUrl ? (
                    <Button href={connectorSwaggerUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Connector Swagger
                    </Button>
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
