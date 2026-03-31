import LaunchRoundedIcon from '@mui/icons-material/LaunchRounded'
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
import { Link } from 'react-router-dom'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

function formatTimestamp(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function swaggerUiUrl(baseUrl: string | null | undefined): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}/swagger-ui/index.html`
}

function roleSummary(role: string): string {
  switch (role) {
    case 'DEPLOYMENT_ADMIN':
      return 'Full deployment administration, including access and destructive operations.'
    case 'DEPLOYMENT_EDITOR':
      return 'Can edit drafts, publish changes, and operate the deployment.'
    case 'DEPLOYMENT_OPERATOR':
      return 'Can apply published versions, rerun verification, and use the POC workspace.'
    case 'DEPLOYMENT_VIEWER':
      return 'Read-only access for diagnostics, versions, and configuration review.'
    default:
      return 'No deployment assignment detected.'
  }
}

function recommendedAction(workspace: NonNullable<ReturnType<typeof useDeploymentWorkspace>['workspace']>): {
  title: string
  description: string
  path: string
} {
  const deploymentId = encodeURIComponent(workspace.deployment.id)
  const latestRelease = workspace.deployment.latestRelease
  if (latestRelease && ['APPLY_REQUESTED', 'PROVISIONING', 'VERIFYING'].includes(latestRelease.status)) {
    return {
      title: 'Track rollout',
      description: 'A release is still in flight. Start with diagnostics and verification evidence.',
      path: `/diagnostics?deploymentId=${deploymentId}`,
    }
  }
  if (workspace.deployment.healthStatus === 'ATTENTION') {
    return {
      title: 'Review diagnostics',
      description: 'The deployment needs attention. Inspect recent release and verification evidence first.',
      path: `/diagnostics?deploymentId=${deploymentId}`,
    }
  }
  if (workspace.access.canEdit && (workspace.deployment.activeVersion == null || workspace.deployment.activeVersion === 'draft' || workspace.deployment.status === 'DRAFT')) {
    return {
      title: 'Continue configuration',
      description: 'The deployment is still draft-led. Review actions, providers, security, and prompts before publishing.',
      path: `/actions?deploymentId=${deploymentId}`,
    }
  }
  if (workspace.access.canOperate && workspace.latestRelease == null) {
    return {
      title: 'Prepare first release',
      description: 'The deployment exists but has not been applied yet. Open versions and launch the first rollout.',
      path: `/revisions?deploymentId=${deploymentId}`,
    }
  }
  if (workspace.access.canOperate) {
    return {
      title: 'Run POC validation',
      description: 'Use the embedded POC workspace to validate prompts, live data, and grounded answers.',
      path: `/poc?deploymentId=${deploymentId}`,
    }
  }
  return {
    title: 'Review deployment state',
    description: 'This workspace is read-only. Start with recent activity, versions, and diagnostics.',
    path: `/activity?deploymentId=${deploymentId}`,
  }
}

export function OverviewPage() {
  const { selectedDeploymentId, workspace, buildWorkspacePath } = useDeploymentWorkspace()

  if (!selectedDeploymentId) {
    return (
      <Stack spacing={3}>
        <Box>
          <Chip label="Overview" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
            Deployment overview
          </Typography>
        </Box>
        <Alert severity="info">Select a deployment to open its overview workspace.</Alert>
      </Stack>
    )
  }

  if (!workspace) {
    return (
      <Stack spacing={3}>
        <Box>
          <Chip label="Overview" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
            Deployment overview
          </Typography>
        </Box>
        <Typography color="text.secondary">Loading deployment overview...</Typography>
      </Stack>
    )
  }

  const action = recommendedAction(workspace)
  const runtimeSwagger = swaggerUiUrl(workspace.deployment.runtimeBaseUrl)
  const connectorSwagger = swaggerUiUrl(workspace.deployment.connectorBaseUrl)

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Overview" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          {workspace.deployment.name}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Start here before jumping into specialist screens. This overview keeps the latest release,
          verification, permissions, endpoints, and next actions in one deployment-first view.
        </Typography>
      </Box>

      <Grid container spacing={2.5}>
        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={1.25}>
                <Typography variant="overline" color="text.secondary">
                  Deployment posture
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {workspace.deployment.healthStatus}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {workspace.deployment.healthSummary}
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={workspace.deployment.status} color="primary" />
                  <Chip label={`Active: ${workspace.deployment.activeVersion ?? 'draft'}`} variant="outlined" />
                  <Chip label={`Environment: ${workspace.deployment.environment}`} variant="outlined" />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={1.25}>
                <Typography variant="overline" color="text.secondary">
                  Latest release
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {workspace.latestRelease?.status ?? 'Not applied'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {workspace.latestRelease?.currentStepDescription ?? 'No apply has been requested yet.'}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Updated {formatTimestamp(workspace.latestRelease?.updatedAt)}
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={`Versions: ${workspace.versionCount}`} variant="outlined" />
                  <Chip label={`Releases: ${workspace.releaseCount}`} variant="outlined" />
                  <Chip label={`Verification runs: ${workspace.verificationRunCount}`} variant="outlined" />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={1.25}>
                <Typography variant="overline" color="text.secondary">
                  Access and guardrails
                </Typography>
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {workspace.access.assignmentRole}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {roleSummary(workspace.access.assignmentRole)}
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip label={workspace.deployment.approvalRequiredForApply ? 'Apply approval required' : 'Apply approval optional'} variant="outlined" />
                  <Chip label={workspace.deployment.approvalRequiredForDelete ? 'Delete approval required' : 'Delete approval optional'} variant="outlined" />
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={2.5}>
        <Grid item xs={12} lg={7}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Recommended next step</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Use the current deployment state and your assignment role to choose the safest next action.
                  </Typography>
                </Box>
                <Alert severity="info">
                  <strong>{action.title}</strong>: {action.description}
                </Alert>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Button component={Link} to={action.path} variant="contained" color="secondary">
                    {action.title}
                  </Button>
                  <Button component={Link} to={buildWorkspacePath('/activity')} variant="outlined">
                    Activity
                  </Button>
                  <Button component={Link} to={buildWorkspacePath('/revisions')} variant="outlined">
                    Versions
                  </Button>
                  <Button component={Link} to={buildWorkspacePath('/diagnostics')} variant="outlined">
                    Diagnostics
                  </Button>
                  {workspace.access.canOperate ? (
                    <Button component={Link} to={buildWorkspacePath('/poc')} variant="outlined">
                      POC
                    </Button>
                  ) : null}
                  {workspace.access.canEdit ? (
                    <Button component={Link} to={buildWorkspacePath('/prompts')} variant="outlined">
                      Prompts
                    </Button>
                  ) : null}
                  {workspace.access.canAdmin ? (
                    <Button component={Link} to={buildWorkspacePath('/access')} variant="outlined">
                      Access
                    </Button>
                  ) : null}
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={5}>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none', height: '100%' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Draft and version state</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Current configuration and publication posture for this deployment.
                  </Typography>
                </Box>
                <Stack spacing={1}>
                  <Typography variant="body2">Draft revision: <strong>r{workspace.draft.revisionNumber}</strong></Typography>
                  <Typography variant="body2">Draft status: <strong>{workspace.draft.status}</strong></Typography>
                  <Typography variant="body2">Latest version: <strong>{workspace.latestVersion?.versionLabel ?? 'None'}</strong></Typography>
                  <Typography variant="body2">Latest verification: <strong>{workspace.latestVerificationRun?.status ?? 'None'}</strong></Typography>
                  <Typography variant="body2">Template: <strong>{workspace.template.name}</strong></Typography>
                  <Typography variant="body2">Source branch: <strong>{workspace.deployment.source.branch}</strong></Typography>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Endpoints and documentation</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Operational links for the currently selected deployment.
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {workspace.deployment.runtimeBaseUrl ? (
                <Button href={workspace.deployment.runtimeBaseUrl} target="_blank" rel="noreferrer" variant="text" startIcon={<LaunchRoundedIcon />}>
                  Runtime
                </Button>
              ) : null}
              {runtimeSwagger ? (
                <Button href={runtimeSwagger} target="_blank" rel="noreferrer" variant="text" startIcon={<LaunchRoundedIcon />}>
                  Runtime Swagger
                </Button>
              ) : null}
              {workspace.deployment.connectorBaseUrl ? (
                <Button href={workspace.deployment.connectorBaseUrl} target="_blank" rel="noreferrer" variant="text" startIcon={<LaunchRoundedIcon />}>
                  Connector
                </Button>
              ) : null}
              {connectorSwagger ? (
                <Button href={connectorSwagger} target="_blank" rel="noreferrer" variant="text" startIcon={<LaunchRoundedIcon />}>
                  Connector Swagger
                </Button>
              ) : null}
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
