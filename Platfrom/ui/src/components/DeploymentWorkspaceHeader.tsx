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
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router-dom'
import {
  fetchDeploymentIntegrationSummary,
  fetchVectorizationOverview,
  type DeploymentWorkspaceSummary,
} from '../api/platformApi'
import {
  connectorIntegrationDescription,
  integrationAlertSeverity,
  integrationModeColor,
  integrationModeLabel,
  runtimeIntegrationDescription,
} from '../workspace/deploymentIntegrationSummary'
import {
  editorBufferStateDisplay,
  liveStateDisplay,
  savedDraftStateDisplay,
} from '../workspace/deploymentWorkspaceLifecycle'
import { DEPLOYMENT_WORKSPACE_PATHS, useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const sectionLabels: Record<(typeof DEPLOYMENT_WORKSPACE_PATHS)[number], string> = {
  '/overview': 'Overview',
  '/activity': 'Activity',
  '/actions': 'Actions',
  '/approvals': 'Approvals',
  '/access': 'Access',
  '/knowledge': 'Knowledge',
  '/poc': 'POC',
  '/prompts': 'Prompts',
  '/providers': 'Providers',
  '/security': 'Security',
  '/verification': 'Verification',
  '/vectorization': 'Vectorization',
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

function joinUrl(baseUrl: string | null | undefined, path: string): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`
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
  if (latestRelease && ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(latestRelease.status)) {
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
  return { label: 'Open overview', path: `/overview?deploymentId=${deploymentId}` }
}

function serviceChipColor(status: string | null | undefined): 'success' | 'warning' | 'error' | 'default' {
  const normalized = (status ?? '').toUpperCase()
  if (['ACTIVE', 'CURRENT', 'UP'].includes(normalized)) {
    return 'success'
  }
  if (['WARNING', 'OUTDATED', 'PENDING', 'QUEUED', 'RUNNING'].includes(normalized)) {
    return 'warning'
  }
  if (['FAILED', 'INCOMPATIBLE', 'ERROR', 'INACTIVE'].includes(normalized)) {
    return 'error'
  }
  return 'default'
}

export function DeploymentWorkspaceHeader() {
  const location = useLocation()
  const {
    deployments,
    deploymentsLoading,
    isScopedPage,
    unresolvedRequestedDeploymentId,
    selectedDeploymentId,
    selectedDeploymentSummary,
    workspace,
    workspaceLoading,
    editorBufferState,
    setSelectedDeploymentId,
    buildWorkspacePath,
  } = useDeploymentWorkspace()

  if (!isScopedPage) {
    return null
  }

  const roleGuidance = workspace ? workspaceRoleGuidance(workspace) : null
  const primaryAction = workspace ? workspacePrimaryAction(workspace) : null
  const runtimeSwaggerUrl = swaggerUiUrl(workspace?.deployment.runtimeBaseUrl)
  const connectorAdminUrl = workspace?.deployment.connectorBaseUrl
    ? joinUrl(workspace?.deployment.runtimeBaseUrl, '/api/admin/connector/overview')
    : null
  const editorState = editorBufferStateDisplay(editorBufferState)
  const savedDraftState = workspace ? savedDraftStateDisplay(workspace.lifecycle) : null
  const liveState = workspace ? liveStateDisplay(workspace.lifecycle) : null
  const vectorizationOverviewQuery = useQuery({
    queryKey: ['workspace-header-vectorization', selectedDeploymentId],
    queryFn: () => fetchVectorizationOverview(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    staleTime: 30_000,
  })
  const integrationSummaryQuery = useQuery({
    queryKey: ['workspace-header-integration-summary', selectedDeploymentId],
    queryFn: () => fetchDeploymentIntegrationSummary(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    staleTime: 30_000,
  })
  const vectorizationOverview = vectorizationOverviewQuery.data
  const integrationSummary = integrationSummaryQuery.data
  const runner = vectorizationOverview?.runner ?? null
  const runnerExpected = vectorizationOverview?.plan?.runnerMode === 'PLATFORM_MANAGED_AUTO'
  const runnerServiceName = runner?.runnerInstanceId?.trim() || (selectedDeploymentId ? `vectorization-runner-${selectedDeploymentId}` : '')

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
                  {workspace?.deployment.name
                    ?? selectedDeploymentSummary?.name
                    ?? (unresolvedRequestedDeploymentId ? 'Deployment not found' : 'Select a deployment')}
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
              <Alert severity={unresolvedRequestedDeploymentId ? 'error' : 'info'}>
                {unresolvedRequestedDeploymentId
                  ? `Deployment ${unresolvedRequestedDeploymentId} is not available. Choose a valid deployment below or use the recovery cards on the page.`
                  : 'Create a deployment from the deployments grid to start using the workspace.'}
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
                  {savedDraftState ? (
                    <Chip label={`Saved draft: ${savedDraftState.label}`} color={savedDraftState.color} />
                  ) : null}
                  {liveState ? (
                    <Chip label={`Live: ${liveState.label}`} color={liveState.color} />
                  ) : null}
                  {editorState ? (
                    <Chip label={editorState.label} color={editorState.color} variant="outlined" />
                  ) : null}
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

                {workspace.deployment.runtimeBaseUrl ? (
                  integrationSummaryQuery.isError ? (
                    <Alert severity="warning">
                      <strong>Integration posture</strong>: Runtime exposure exists, but the auth-mode summary could not be loaded. Treat customer-facing integrations conservatively and prefer backend-mediated private runtime until the posture is confirmed.
                    </Alert>
                  ) : integrationSummary ? (
                    <Alert severity={integrationAlertSeverity(integrationSummary)}>
                      <strong>Integration posture</strong>: {integrationSummary.guidance ?? 'Apply the deployment before integrating.'}
                    </Alert>
                  ) : null
                ) : null}

                {savedDraftState && liveState ? (
                  <Alert severity={editorBufferState?.dirty ? 'warning' : liveState.severity}>
                    <strong>State clarity</strong>: {workspace.lifecycle.summaryMessage}
                    {editorState ? ` ${editorState.description}` : ''}
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
                  <Button component={Link} to={buildWorkspacePath('/activity')} variant="outlined" size="small">
                    Activity
                  </Button>
                  {workspace.access.canOperate && workspace.deployment.runtimeBaseUrl ? (
                    <Button href={workspace.deployment.runtimeBaseUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Runtime service
                    </Button>
                  ) : null}
                  {workspace.access.canOperate && runtimeSwaggerUrl ? (
                    <Button href={runtimeSwaggerUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Runtime Swagger
                    </Button>
                  ) : null}
                  {workspace.access.canOperate && connectorAdminUrl ? (
                    <Button href={connectorAdminUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                      Connector admin
                    </Button>
                  ) : null}
                </Stack>

                <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                  <Card sx={{ flex: 1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                    <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="subtitle2">Runtime service</Typography>
                          <Chip
                            size="small"
                            label={workspace.deployment.runtimeBaseUrl ? integrationModeLabel(integrationSummary) : 'Not applied'}
                            color={workspace.deployment.runtimeBaseUrl ? integrationModeColor(integrationSummary) : 'default'}
                          />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {runtimeIntegrationDescription(workspace.deployment.runtimeBaseUrl, integrationSummary)}
                        </Typography>
                        {integrationSummary ? (
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip size="small" label={`Mode: ${integrationSummary.runtimeAuthMode ?? 'UNKNOWN'}`} variant="outlined" />
                            {integrationSummary.hostBackedRuntimeRequired ? (
                              <Chip size="small" label="Host-backed required" color="success" variant="outlined" />
                            ) : null}
                            {integrationSummary.publicRuntimeTokenValidationConfigured ? (
                              <Chip size="small" label="Signed public token ready" color="success" variant="outlined" />
                            ) : null}
                            {integrationSummary.anonymousBootstrapSupported ? (
                              <Chip size="small" label="Anonymous bootstrap" color="warning" variant="outlined" />
                            ) : null}
                          </Stack>
                        ) : null}
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {workspace.access.canOperate && workspace.deployment.runtimeBaseUrl ? (
                            <Button href={workspace.deployment.runtimeBaseUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                              Open runtime
                            </Button>
                          ) : null}
                          {workspace.access.canOperate && runtimeSwaggerUrl ? (
                            <Button href={runtimeSwaggerUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                              Swagger
                            </Button>
                          ) : null}
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>

                  <Card sx={{ flex: 1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                    <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="subtitle2">REST connector service</Typography>
                          <Chip
                            size="small"
                            label={workspace.deployment.connectorBaseUrl
                              ? integrationSummary?.connectorInternalOnly === false
                                ? 'Exposure review needed'
                                : 'Internal service'
                              : 'Not applied'}
                            color={workspace.deployment.connectorBaseUrl
                              ? integrationSummary?.connectorInternalOnly === false ? 'warning' : 'success'
                              : 'default'}
                          />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {connectorIntegrationDescription(workspace.deployment.connectorBaseUrl, integrationSummary)}
                        </Typography>
                        {integrationSummary ? (
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip
                              size="small"
                              label={integrationSummary.connectorInternalOnly ? 'Runtime-backed reads only' : 'Direct connector access review'}
                              color={integrationSummary.connectorInternalOnly ? 'success' : 'warning'}
                              variant="outlined"
                            />
                            {integrationSummary.preferredCrudBaseUrl ? (
                              <Chip size="small" label="CRUD/read surface: runtime" variant="outlined" />
                            ) : null}
                          </Stack>
                        ) : null}
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {workspace.access.canOperate && connectorAdminUrl ? (
                            <Button href={connectorAdminUrl} target="_blank" rel="noreferrer" variant="text" size="small">
                              Admin via runtime
                            </Button>
                          ) : null}
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>

                  <Card sx={{ flex: 1, border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                    <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} justifyContent="space-between" alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="subtitle2">Vectorization runner</Typography>
                          {runner ? (
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip size="small" label={runner.registrationStatus} color={serviceChipColor(runner.registrationStatus)} />
                              <Chip size="small" label={runner.compatibilityStatus} color={serviceChipColor(runner.compatibilityStatus)} variant="outlined" />
                            </Stack>
                          ) : (
                            <Chip
                              size="small"
                              label={runnerExpected ? 'Expected' : 'Not configured'}
                              color={runnerExpected ? 'warning' : 'default'}
                            />
                          )}
                        </Stack>
                        {vectorizationOverviewQuery.isLoading ? (
                          <Typography variant="body2" color="text.secondary">
                            Loading runner state...
                          </Typography>
                        ) : vectorizationOverviewQuery.isError ? (
                          <Typography variant="body2" color="error.main">
                            Unable to load runner state from the vectorization control plane.
                          </Typography>
                        ) : runner ? (
                          <>
                            <Typography variant="body2" color="text.secondary">
                              Service: <strong>{runnerServiceName}</strong>. Platform-managed runners are pull workers and do not expose a public base URL like runtime or connector.
                            </Typography>
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip size="small" label={`Mode: ${runner.runnerMode}`} variant="outlined" />
                              {runner.productVersion ? (
                                <Chip size="small" label={`Product: ${runner.productVersion}`} variant="outlined" />
                              ) : null}
                              {runner.lastSessionHeartbeatAt ? (
                                <Chip size="small" label={`Heartbeat: ${new Date(runner.lastSessionHeartbeatAt).toLocaleString()}`} variant="outlined" />
                              ) : null}
                            </Stack>
                          </>
                        ) : runnerExpected ? (
                          <Typography variant="body2" color="warning.main">
                            This deployment expects a platform-managed runner, but no active registration is currently visible.
                          </Typography>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            This deployment is not currently using a managed vectorization runner.
                          </Typography>
                        )}
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Button component={Link} to={buildWorkspacePath('/vectorization')} variant="text" size="small">
                            Open vectorization
                          </Button>
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>
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
