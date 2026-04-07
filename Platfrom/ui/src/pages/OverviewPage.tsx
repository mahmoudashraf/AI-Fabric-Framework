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
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  type DeploymentRailwayLiveServiceSummary,
  fetchDeploymentDraft,
  fetchDeploymentIntegrationSummary,
  fetchDeploymentPocPromptSession,
  fetchDeploymentPocWorkspace,
  fetchDeploymentPromptBaseline,
  fetchDeploymentProductionReadiness,
  fetchDeploymentServiceConfigModel,
  fetchDeploymentServiceNavigation,
  fetchDeploymentSourceOfTruth,
} from '../api/platformApi'
import {
  editorBufferStateDisplay,
  liveStateDisplay,
  savedDraftStateDisplay,
} from '../workspace/deploymentWorkspaceLifecycle'
import {
  integrationAlertSeverity,
  integrationModeColor,
  integrationModeLabel,
  runtimeIntegrationDescription,
} from '../workspace/deploymentIntegrationSummary'
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

function joinUrl(baseUrl: string | null | undefined, path: string): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function countActions(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }
  const actions = config.actions
  return Array.isArray(actions) ? actions.length : 0
}

function countEntitySpaces(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }
  const entities = config['ai-entities']
  return isRecord(entities) ? Object.keys(entities).length : 0
}

function countRoutingActions(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }
  const actions = config.actions
  return isRecord(actions) ? Object.keys(actions).length : 0
}

function countPromptEntries(config: unknown): number {
  if (!isRecord(config)) {
    return 0
  }
  return Object.values(config).filter((value) => typeof value === 'string' && value.trim().length > 0).length
}

function readinessColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (status === 'READY') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'BLOCKED') {
    return 'error'
  }
  return 'default'
}

function serviceStatusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  return readinessColor(status)
}

function alertSeverityForStatus(
  status: string,
  available: boolean,
): 'success' | 'info' | 'warning' | 'error' {
  if (!available) {
    return 'info'
  }
  if (status === 'READY') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'BLOCKED') {
    return 'error'
  }
  return 'info'
}

function driftChipColor(state: string): 'success' | 'warning' | 'error' | 'default' {
  switch (state) {
    case 'MATCHED':
      return 'success'
    case 'MISSING':
      return 'warning'
    case 'MISMATCHED':
      return 'error'
    default:
      return 'default'
  }
}

function deletionChipColor(
  status: string,
): 'default' | 'info' | 'warning' | 'error' | 'success' {
  switch (status) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warning'
    case 'FAILED':
      return 'error'
    case 'SUCCEEDED':
      return 'success'
    default:
      return 'default'
  }
}

function summarizeEnvDrift(
  service: DeploymentRailwayLiveServiceSummary,
  state: 'MISSING' | 'MISMATCHED',
): string {
  const keys = service.envVars
    .filter((item) => item.driftState === state)
    .map((item) => item.key)
  return keys.length > 0 ? keys.join(', ') : 'None'
}

function surfaceTypeLabel(surfaceType: string): string {
  switch (surfaceType) {
    case 'PROVISIONED_SERVICE':
      return 'Platform-managed service'
    case 'EXTERNAL_DEPENDENCY':
      return 'External dependency'
    case 'CLIENT_SURFACE':
      return 'Client-facing surface'
    case 'PLATFORM_STACK':
      return 'Platform-managed stack'
    default:
      return 'Deployment surface'
  }
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
  if (latestRelease && ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(latestRelease.status)) {
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
  const { selectedDeploymentId, workspace, editorBufferState, buildWorkspacePath } = useDeploymentWorkspace()
  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const baselineQuery = useQuery({
    queryKey: ['deployment-prompt-baseline', selectedDeploymentId],
    queryFn: () => fetchDeploymentPromptBaseline(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const promptSessionQuery = useQuery({
    queryKey: ['deployment-poc-prompt-session', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocPromptSession(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const serviceConfigModelQuery = useQuery({
    queryKey: ['deployment-service-config-model', selectedDeploymentId],
    queryFn: () => fetchDeploymentServiceConfigModel(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const sourceOfTruthQuery = useQuery({
    queryKey: ['deployment-source-of-truth', selectedDeploymentId],
    queryFn: () => fetchDeploymentSourceOfTruth(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const integrationSummaryQuery = useQuery({
    queryKey: ['deployment-integration-summary', selectedDeploymentId],
    queryFn: () => fetchDeploymentIntegrationSummary(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    staleTime: 30_000,
  })
  const navigationQuery = useQuery({
    queryKey: ['deployment-service-navigation', selectedDeploymentId],
    queryFn: () => fetchDeploymentServiceNavigation(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const productionReadinessQuery = useQuery({
    queryKey: ['deployment-production-readiness', selectedDeploymentId],
    queryFn: () => fetchDeploymentProductionReadiness(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const pocWorkspaceQuery = useQuery({
    queryKey: ['deployment-poc-workspace', selectedDeploymentId],
    queryFn: () => fetchDeploymentPocWorkspace(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

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
  const savedDraftState = savedDraftStateDisplay(workspace.lifecycle)
  const liveState = liveStateDisplay(workspace.lifecycle)
  const editorState = editorBufferStateDisplay(editorBufferState)
  const draft = draftQuery.data
  const pocWorkspace = pocWorkspaceQuery.data
  const promptSession = promptSessionQuery.data
  const actionsCount = countActions(draft?.actionsConfig)
  const entitySpacesCount = countEntitySpaces(draft?.entityConfig)
  const routingActionsCount = countRoutingActions(draft?.routingConfig)
  const draftPromptCount = countPromptEntries(draft?.promptConfig)
  const publishedPromptCount = baselineQuery.data?.populatedPromptCount ?? 0
  const totalVectors = pocWorkspace?.indexing.totalVectors ?? 0
  const recentImportCount = pocWorkspace?.recentImports.length ?? 0
  const sourceOfTruth = sourceOfTruthQuery.data
  const integrationSummary = integrationSummaryQuery.data
  const runtimeAuthOverview = integrationSummary?.preferredAuthOverviewUrl
    ?? (workspace.deployment.runtimeBaseUrl
      ? joinUrl(workspace.deployment.runtimeBaseUrl, '/api/admin/auth/overview')
      : null)
  const connectorAdminOverview = integrationSummary?.preferredConnectorOverviewUrl
    ?? (workspace.deployment.connectorBaseUrl
      ? joinUrl(workspace.deployment.runtimeBaseUrl, '/api/admin/connector/overview')
      : null)

  const readinessChecks = [
    {
      key: 'prompts',
      label: 'Prompts',
      status: draftPromptCount > 0 || publishedPromptCount > 0 ? 'READY' : 'WARNING',
      message: draftPromptCount > 0 || publishedPromptCount > 0
        ? `Draft prompts: ${draftPromptCount}. Published prompts: ${publishedPromptCount}.`
        : 'No populated prompt bundle is visible yet.',
    },
    {
      key: 'actions',
      label: 'Actions',
      status: actionsCount > 0 && routingActionsCount > 0 ? 'READY' : 'WARNING',
      message: actionsCount > 0
        ? `${actionsCount} action definition(s), ${routingActionsCount} routed action path(s).`
        : 'No action definitions are visible in the current draft.',
    },
    {
      key: 'knowledge',
      label: 'Knowledge',
      status: entitySpacesCount > 0 ? 'READY' : 'BLOCKED',
      message: entitySpacesCount > 0
        ? `${entitySpacesCount} entity space(s) configured in the draft.`
        : 'No entity spaces are configured yet for retrieval or indexing.',
    },
    {
      key: 'runtime',
      label: 'Runtime',
      status: workspace.deployment.runtimeBaseUrl
        ? integrationSummary?.preferredIntegrationMode === 'AUTH_CONFIGURATION_REQUIRED' ? 'WARNING' : 'READY'
        : 'BLOCKED',
      message: workspace.deployment.runtimeBaseUrl
        ? integrationSummary
          ? runtimeIntegrationDescription(workspace.deployment.runtimeBaseUrl, integrationSummary)
          : workspace.deployment.connectorBaseUrl
            ? 'Runtime service is available. Connector is applied as an internal/operator surface, with supported connector admin reads exposed through runtime.'
            : 'Runtime service is available. Connector internal surface is not yet applied.'
        : 'Apply the deployment so the runtime service exists before deeper validation.',
    },
    {
      key: 'poc-data',
      label: 'POC Data',
      status: totalVectors > 0 || recentImportCount > 0 ? 'READY' : 'WARNING',
      message: totalVectors > 0 || recentImportCount > 0
        ? `${totalVectors} indexed vector(s) and ${recentImportCount} recent import run(s) are visible.`
        : 'No proof-of-concept dataset has been loaded yet.',
    },
  ] as const

  const blockedChecks = readinessChecks.filter((check) => check.status === 'BLOCKED')
  const warningChecks = readinessChecks.filter((check) => check.status === 'WARNING')
  const readinessMessage = blockedChecks.length > 0
    ? blockedChecks[0].key === 'runtime'
      ? 'Apply the deployment first so the runtime service exists before deeper validation.'
      : blockedChecks[0].key === 'knowledge'
        ? 'Configure entity spaces before positioning this deployment as a grounded assistant.'
        : 'Resolve blocked readiness checks before customer-facing validation.'
    : warningChecks.some((check) => check.key === 'poc-data')
      ? 'The assistant shell is in place, but you still need proof-of-concept data for grounded validation.'
      : 'The core assistant surface is ready. Use the POC workspace to run scenario and trace validation.'

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
        <Alert severity={editorBufferState?.dirty ? 'warning' : liveState.severity} sx={{ mt: 2 }}>
          <strong>State clarity</strong>: {workspace.lifecycle.summaryMessage}
          {editorState ? ` ${editorState.description}` : ''}
        </Alert>
        {workspace.deployment.deletion ? (
          <Alert
            severity={workspace.deployment.deletion.status === 'FAILED' ? 'error' : 'info'}
            sx={{ mt: 2 }}
          >
            <strong>Deletion status</strong>: {workspace.deployment.deletion.message}
          </Alert>
        ) : null}
        {workspace.deployment.runtimeBaseUrl ? (
          integrationSummaryQuery.isError ? (
            <Alert severity="warning" sx={{ mt: 2 }}>
              <strong>Integration posture</strong>: Runtime exposure exists, but the auth-mode summary could not be loaded. Prefer backend-mediated private-runtime integration until the posture is confirmed.
            </Alert>
          ) : integrationSummary ? (
            <Alert severity={integrationAlertSeverity(integrationSummary)} sx={{ mt: 2 }}>
              <strong>Integration posture</strong>: {integrationSummary.guidance ?? 'Apply the deployment before integrating.'}
            </Alert>
          ) : null
        ) : null}
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
                  {workspace.deployment.runtimeBaseUrl && integrationSummary ? (
                    <Chip
                      label={integrationModeLabel(integrationSummary)}
                      color={integrationModeColor(integrationSummary)}
                      variant="outlined"
                    />
                  ) : null}
                  {integrationSummary?.runtimeAuthMode ? (
                    <Chip label={`Auth: ${integrationSummary.runtimeAuthMode}`} variant="outlined" />
                  ) : null}
                  <Chip label={savedDraftState.label} color={savedDraftState.color} variant="outlined" />
                  <Chip label={liveState.label} color={liveState.color} variant="outlined" />
                  {workspace.deployment.deletion ? (
                    <Chip
                      label={`Deletion ${workspace.deployment.deletion.status}`}
                      color={deletionChipColor(workspace.deployment.deletion.status)}
                      variant="outlined"
                    />
                  ) : null}
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
                    Distinguish browser-only edits, saved draft state, and the currently live applied posture.
                  </Typography>
                </Box>
                <Alert severity={savedDraftState.severity}>
                  <strong>Saved draft</strong>: {savedDraftState.description}
                </Alert>
                <Alert severity={liveState.severity}>
                  <strong>Live deployment</strong>: {liveState.description}
                </Alert>
                {editorState ? (
                  <Alert severity={editorState.severity}>
                    <strong>Editor buffer</strong>: {editorState.description}
                  </Alert>
                ) : null}
                <Stack spacing={1}>
                  <Typography variant="body2">Draft revision: <strong>r{workspace.draft.revisionNumber}</strong></Typography>
                  <Typography variant="body2">Draft status: <strong>{workspace.draft.status}</strong></Typography>
                  <Typography variant="body2">Latest published version: <strong>{workspace.lifecycle.latestPublishedVersionLabel ?? 'None'}</strong></Typography>
                  <Typography variant="body2">Live version: <strong>{workspace.lifecycle.liveVersionLabel ?? 'None'}</strong></Typography>
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
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Source of truth and provenance</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                Track which template, source branch, immutable version hash, and generated artifact bundle
                currently define the deployment. This is the operator view of what actually produced live state.
              </Typography>
            </Box>

            {sourceOfTruthQuery.isLoading ? (
              <Typography color="text.secondary">Loading deployment provenance...</Typography>
            ) : sourceOfTruthQuery.isError ? (
              <Alert severity="error">
                {sourceOfTruthQuery.error instanceof Error
                  ? sourceOfTruthQuery.error.message
                  : 'Failed to load deployment provenance.'}
              </Alert>
            ) : sourceOfTruth ? (
              <>
                <Alert
                  severity={
                    sourceOfTruth.live.available && sourceOfTruth.latestPublished.available && sourceOfTruth.live.referenceId === sourceOfTruth.latestPublished.referenceId
                      ? 'success'
                      : sourceOfTruth.latestPublished.available
                        ? 'info'
                        : 'warning'
                  }
                >
                  {sourceOfTruth.summaryMessage}
                </Alert>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Template baseline
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            {sourceOfTruth.templateSource.templateName}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {sourceOfTruth.templateSource.templateDescription}
                          </Typography>
                          <Typography variant="body2">
                            LLM provider: <strong>{sourceOfTruth.templateSource.llmProvider}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Embedding provider: <strong>{sourceOfTruth.templateSource.embeddingProvider}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Vector strategy: <strong>{sourceOfTruth.templateSource.vectorStrategy}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Runtime profile: <strong>{sourceOfTruth.templateSource.runtimeProfile}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Connector profile: <strong>{sourceOfTruth.templateSource.connectorProfile}</strong>
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Source branch
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            {sourceOfTruth.generated.branch ?? sourceOfTruth.templateSource.branch}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Repository: {sourceOfTruth.generated.repository ?? sourceOfTruth.templateSource.repository}
                          </Typography>
                          <Chip
                            label={sourceOfTruth.templateSource.overrideActive ? 'Deployment override active' : 'Template default source'}
                            size="small"
                            color={sourceOfTruth.templateSource.overrideActive ? 'secondary' : 'default'}
                            variant="outlined"
                          />
                          <Typography variant="body2">
                            Draft reference: <strong>{sourceOfTruth.draft.referenceLabel}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Latest published: <strong>{sourceOfTruth.latestPublished.referenceLabel}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Live reference: <strong>{sourceOfTruth.live.referenceLabel}</strong>
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Generated deployment
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            {sourceOfTruth.generated.projectName ?? 'No generated project yet'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Provisioning mode: {sourceOfTruth.generated.provisioningMode ?? 'Unavailable'}
                          </Typography>
                          <Typography variant="body2">
                            Artifact strategy: <strong>{sourceOfTruth.generated.artifactStrategy ?? 'Unavailable'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Runtime service: <strong>{sourceOfTruth.generated.runtimeServiceName ?? 'Pending publish'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            REST service: <strong>{sourceOfTruth.generated.restConnectorServiceName ?? 'Pending publish'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Runner service: <strong>{sourceOfTruth.generated.vectorizationRunnerServiceName ?? 'Not provisioned'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Runtime URL: <strong>{sourceOfTruth.generated.runtimeBaseUrl ?? 'Not applied'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Connector URL (internal): <strong>{sourceOfTruth.generated.connectorBaseUrl ?? 'Not applied'}</strong>
                          </Typography>
                          {integrationSummary ? (
                            <>
                              <Typography variant="body2">
                                Preferred integration: <strong>{integrationModeLabel(integrationSummary)}</strong>
                              </Typography>
                              <Typography variant="body2">
                                Runtime auth mode: <strong>{integrationSummary.runtimeAuthMode ?? 'Unavailable'}</strong>
                              </Typography>
                            </>
                          ) : null}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Release lineage
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            {sourceOfTruth.latestRelease?.status ?? 'No release yet'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {sourceOfTruth.latestRelease?.currentStepDescription ?? 'No apply has been requested yet.'}
                          </Typography>
                          <Typography variant="body2">
                            Draft updated: <strong>{formatTimestamp(sourceOfTruth.draft.updatedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Published at: <strong>{formatTimestamp(sourceOfTruth.latestPublished.updatedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Live timestamp: <strong>{formatTimestamp(sourceOfTruth.live.updatedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Latest hash: <strong>{sourceOfTruth.latestPublished.configHash ?? 'None'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Live hash: <strong>{sourceOfTruth.live.configHash ?? 'None'}</strong>
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Managed vector resources
                          </Typography>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography variant="h6" sx={{ fontWeight: 800 }}>
                              {sourceOfTruth.managedVector.activeResourceCount} active
                            </Typography>
                            <Chip
                              label={sourceOfTruth.managedVector.status}
                              color={serviceStatusColor(sourceOfTruth.managedVector.status)}
                              size="small"
                            />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {sourceOfTruth.managedVector.summaryMessage}
                          </Typography>
                          <Typography variant="body2">
                            Strategy: <strong>{sourceOfTruth.managedVector.vectorStrategy}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Provisioning mode: <strong>{sourceOfTruth.managedVector.vectorProvisioningMode}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Detached history: <strong>{sourceOfTruth.managedVector.detachedResourceCount}</strong>
                          </Typography>
                          {sourceOfTruth.managedVector.driftDetected ? (
                            <Typography variant="body2" color="warning.main">
                              Drift: <strong>{sourceOfTruth.managedVector.driftedResourceCount}</strong> resource target(s) need reconciliation.
                            </Typography>
                          ) : null}
                          <Stack spacing={0.75}>
                            {sourceOfTruth.managedVector.resources.slice(0, 3).map((resource) => (
                              <Typography key={resource.id} variant="body2">
                                <strong>{resource.vendor}</strong> {resource.resourceType.toLowerCase()}:{' '}
                                {resource.resourceName} ({resource.resourceStatus.toLowerCase()}
                                {resource.driftState !== 'ALIGNED' && resource.driftState !== 'DETACHED_HISTORY'
                                  ? `, ${resource.driftState.toLowerCase()}`
                                  : ''}
                                {resource.deletionStatus ? `, delete ${resource.deletionStatus.toLowerCase()}` : ''}
                                )
                              </Typography>
                            ))}
                            {sourceOfTruth.managedVector.resources.length === 0 ? (
                              <Typography variant="body2" color="text.secondary">
                                No managed vector resource records exist for this deployment yet.
                              </Typography>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={6} xl={3}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Tenant-scoped vector scope
                          </Typography>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography variant="h6" sx={{ fontWeight: 800 }}>
                              {sourceOfTruth.tenantScopedVector.vectorStoragePosture}
                            </Typography>
                            <Chip
                              label={sourceOfTruth.tenantScopedVector.status}
                              color={serviceStatusColor(sourceOfTruth.tenantScopedVector.status)}
                              size="small"
                            />
                            {sourceOfTruth.tenantScopedVector.registry ? (
                              <Chip
                                label={`Registry ${sourceOfTruth.tenantScopedVector.registry.status}`}
                                color={serviceStatusColor(sourceOfTruth.tenantScopedVector.registry.status)}
                                size="small"
                                variant="outlined"
                              />
                            ) : null}
                            {sourceOfTruth.tenantScopedVector.registry ? (
                              <Chip
                                label={`Cleanup ${sourceOfTruth.tenantScopedVector.registry.cleanupReadinessStatus}`}
                                color={serviceStatusColor(sourceOfTruth.tenantScopedVector.registry.cleanupReadinessStatus)}
                                size="small"
                                variant="outlined"
                              />
                            ) : null}
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {sourceOfTruth.tenantScopedVector.summaryMessage}
                          </Typography>
                          {sourceOfTruth.tenantScopedVector.registry ? (
                            <Typography variant="body2" color="text.secondary">
                              {sourceOfTruth.tenantScopedVector.registry.message}
                            </Typography>
                          ) : null}
                          {sourceOfTruth.tenantScopedVector.registry ? (
                            <Typography variant="body2" color="text.secondary">
                              {sourceOfTruth.tenantScopedVector.registry.cleanupReadinessMessage}
                            </Typography>
                          ) : null}
                          <Typography variant="body2">
                            Customer / tenant:{' '}
                            <strong>
                              {sourceOfTruth.tenantScopedVector.customerName} / {sourceOfTruth.tenantScopedVector.tenantName}
                            </strong>
                          </Typography>
                          <Typography variant="body2">
                            Lifecycle owner: <strong>{sourceOfTruth.tenantScopedVector.lifecycleOwner}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Scope type: <strong>{sourceOfTruth.tenantScopedVector.scopeType}</strong>
                          </Typography>
                          {sourceOfTruth.tenantScopedVector.rootResourceLabel && sourceOfTruth.tenantScopedVector.rootResourceValue ? (
                            <Typography variant="body2">
                              {sourceOfTruth.tenantScopedVector.rootResourceLabel}:{' '}
                              <strong>{sourceOfTruth.tenantScopedVector.rootResourceValue}</strong>
                            </Typography>
                          ) : null}
                          {sourceOfTruth.tenantScopedVector.scopePrefix ? (
                            <Typography variant="body2">
                              Scope prefix: <strong>{sourceOfTruth.tenantScopedVector.scopePrefix}</strong>
                            </Typography>
                          ) : null}
                          {sourceOfTruth.tenantScopedVector.tenantHandle ? (
                            <Typography variant="body2">
                              Tenant handle: <strong>{sourceOfTruth.tenantScopedVector.tenantHandle}</strong>
                            </Typography>
                          ) : null}
                          {sourceOfTruth.tenantScopedVector.scopePattern ? (
                            <Typography variant="body2">
                              Scope pattern: <strong>{sourceOfTruth.tenantScopedVector.scopePattern}</strong>
                            </Typography>
                          ) : null}
                          {sourceOfTruth.tenantScopedVector.registry ? (
                            <Typography variant="body2">
                              Active / historical registry records:{' '}
                              <strong>
                                {sourceOfTruth.tenantScopedVector.registry.activeRecordCount} / {sourceOfTruth.tenantScopedVector.registry.historicalRecordCount}
                              </strong>
                            </Typography>
                          ) : null}
                          <Typography
                            variant="body2"
                            color={sourceOfTruth.tenantScopedVector.migrationLocked ? 'warning.main' : 'text.secondary'}
                          >
                            {sourceOfTruth.tenantScopedVector.migrationMessage}
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>

                <Grid container spacing={2}>
                  <Grid item xs={12} lg={6}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                            Latest published artifacts
                          </Typography>
                          {sourceOfTruth.latestPublishedArtifacts ? (
                            <>
                              <Typography variant="body2" color="text.secondary">
                                {sourceOfTruth.latestPublishedArtifacts.versionLabel} • {sourceOfTruth.latestPublishedArtifacts.configHash}
                              </Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Button href={sourceOfTruth.latestPublishedArtifacts.actionsArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Actions
                                </Button>
                                <Button href={sourceOfTruth.latestPublishedArtifacts.entityArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Entities
                                </Button>
                                <Button href={sourceOfTruth.latestPublishedArtifacts.routingArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Routing
                                </Button>
                                <Button href={sourceOfTruth.latestPublishedArtifacts.promptArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Prompts
                                </Button>
                                <Button href={sourceOfTruth.latestPublishedArtifacts.manifestUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Manifest
                                </Button>
                              </Stack>
                            </>
                          ) : (
                            <Typography color="text.secondary">No published artifact bundle exists yet.</Typography>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} lg={6}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.5}>
                          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                            Live applied artifacts
                          </Typography>
                          {sourceOfTruth.liveArtifacts ? (
                            <>
                              <Typography variant="body2" color="text.secondary">
                                {sourceOfTruth.liveArtifacts.versionLabel} • {sourceOfTruth.liveArtifacts.configHash}
                              </Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Button href={sourceOfTruth.liveArtifacts.actionsArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Actions
                                </Button>
                                <Button href={sourceOfTruth.liveArtifacts.entityArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Entities
                                </Button>
                                <Button href={sourceOfTruth.liveArtifacts.routingArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Routing
                                </Button>
                                <Button href={sourceOfTruth.liveArtifacts.promptArtifactUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Prompts
                                </Button>
                                <Button href={sourceOfTruth.liveArtifacts.manifestUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                  Manifest
                                </Button>
                              </Stack>
                            </>
                          ) : (
                            <Typography color="text.secondary">No live applied artifact bundle exists yet.</Typography>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>

                <Card variant="outlined">
                  <CardContent>
                    <Stack spacing={2}>
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                          Railway live provider read-back
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          Compare the platform-managed live plan against Railway&apos;s current service instance,
                          repository trigger, domain, and env-var state.
                        </Typography>
                      </Box>

                      <Alert
                        severity={alertSeverityForStatus(
                          sourceOfTruth.liveRailwayReadback.status,
                          sourceOfTruth.liveRailwayReadback.available,
                        )}
                      >
                        {sourceOfTruth.liveRailwayReadback.summaryMessage}
                      </Alert>

                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip
                          label={`Project: ${sourceOfTruth.liveRailwayReadback.projectName ?? 'Unavailable'}`}
                          variant="outlined"
                        />
                        <Chip
                          label={`Environment: ${sourceOfTruth.liveRailwayReadback.environmentName ?? 'Unavailable'}`}
                          variant="outlined"
                        />
                        <Chip
                          label={`Status: ${sourceOfTruth.liveRailwayReadback.status}`}
                          color={serviceStatusColor(sourceOfTruth.liveRailwayReadback.status)}
                        />
                      </Stack>

                      <Grid container spacing={2}>
                        {[
                          sourceOfTruth.liveRailwayReadback.runtime,
                          sourceOfTruth.liveRailwayReadback.restConnector,
                          sourceOfTruth.liveRailwayReadback.vectorizationRunner,
                        ]
                          .filter((service): service is NonNullable<typeof sourceOfTruth.liveRailwayReadback.vectorizationRunner> => Boolean(service))
                          .map((service) => (
                          <Grid item xs={12} md={6} key={service.key}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                              <CardContent>
                                <Stack spacing={1.5}>
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                      {service.label}
                                    </Typography>
                                    <Chip label={service.status} color={serviceStatusColor(service.status)} size="small" />
                                  </Stack>

                                  <Typography variant="body2" color="text.secondary">
                                    {service.summaryMessage}
                                  </Typography>

                                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                    {[service.rootDirectory, service.dockerfilePath, service.repository, service.branch, service.publicBaseUrl].map((field) => (
                                      <Chip
                                        key={`${service.key}-${field.key}`}
                                        label={`${field.label}: ${field.driftState.toLowerCase()}`}
                                        color={driftChipColor(field.driftState)}
                                        variant="outlined"
                                        size="small"
                                      />
                                    ))}
                                  </Stack>

                                  <Typography variant="body2">
                                    Service id: <strong>{service.serviceId ?? 'Unavailable'}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Expected env vars: <strong>{service.expectedEnvCount}</strong> • matched:{' '}
                                    <strong>{service.matchingEnvCount}</strong> • missing:{' '}
                                    <strong>{service.missingEnvCount}</strong> • mismatched:{' '}
                                    <strong>{service.mismatchedEnvCount}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Missing env keys: <strong>{summarizeEnvDrift(service, 'MISSING')}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Mismatched env keys: <strong>{summarizeEnvDrift(service, 'MISMATCHED')}</strong>
                                  </Typography>
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ))}
                      </Grid>
                    </Stack>
                  </CardContent>
                </Card>
              </>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Unified deployment surface model</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                Runtime and REST connector are platform-provisioned services. Browser, upstream, and provider
                entries are shown alongside them so operators can govern the full deployment surface without
                confusing external dependencies for Railway-managed services.
              </Typography>
            </Box>

            {serviceConfigModelQuery.isLoading ? (
              <Typography color="text.secondary">Loading service configuration model...</Typography>
            ) : serviceConfigModelQuery.isError ? (
              <Alert severity="error">
                {serviceConfigModelQuery.error instanceof Error
                  ? serviceConfigModelQuery.error.message
                  : 'Failed to load deployment service configuration model.'}
              </Alert>
            ) : serviceConfigModelQuery.data ? (
              <>
                <Alert severity={serviceConfigModelQuery.data.services.some((service) => service.status === 'BLOCKED')
                  ? 'warning'
                  : serviceConfigModelQuery.data.services.some((service) => service.status === 'WARNING')
                    ? 'info'
                    : 'success'}
                >
                  {serviceConfigModelQuery.data.summaryMessage}
                </Alert>

                <Grid container spacing={2}>
                  {serviceConfigModelQuery.data.services.map((service) => (
                    <Grid item xs={12} md={6} xl={4} key={service.key}>
                      <Card variant="outlined" sx={{ height: '100%' }}>
                        <CardContent>
                          <Stack spacing={1.5}>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {service.label}
                              </Typography>
                              <Chip
                                label={surfaceTypeLabel(service.surfaceType)}
                                size="small"
                                variant="outlined"
                                color={service.platformManaged ? 'secondary' : 'default'}
                              />
                              <Chip
                                label={service.status}
                                size="small"
                                color={serviceStatusColor(service.status)}
                                variant="outlined"
                              />
                            </Stack>

                            <Typography variant="body2" color="text.secondary">
                              {service.purpose}
                            </Typography>

                            <Typography variant="body2">
                              Required fields: <strong>{service.configuredRequiredFieldCount}/{service.requiredFieldCount}</strong>
                            </Typography>

                            {service.baseUrl ? (
                              <Typography variant="body2" color="text.secondary">
                                Base URL: {service.baseUrl}
                              </Typography>
                            ) : null}

                            <Alert severity={service.status === 'BLOCKED' ? 'error' : service.status === 'WARNING' ? 'warning' : 'success'}>
                              {service.summaryMessage}
                            </Alert>

                            <Stack spacing={1}>
                              {service.fields.map((field) => (
                                <Card key={field.key} variant="outlined" sx={{ boxShadow: 'none' }}>
                                  <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                                    <Stack spacing={0.75}>
                                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                          {field.label}
                                        </Typography>
                                        {field.required ? <Chip label="Required" size="small" variant="outlined" /> : null}
                                        <Chip
                                          label={field.configured ? 'Configured' : 'Missing'}
                                          size="small"
                                          color={field.configured ? 'success' : field.required ? 'error' : 'default'}
                                          variant="outlined"
                                        />
                                      </Stack>
                                      <Typography variant="body2">{field.valueSummary}</Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Source: {field.source} • {field.guidance}
                                      </Typography>
                                    </Stack>
                                  </CardContent>
                                </Card>
                              ))}
                            </Stack>

                            {service.issues.length > 0 ? (
                              <Stack spacing={1}>
                                {service.issues.slice(0, 3).map((issue) => (
                                  <Alert
                                    key={`${service.key}:${issue.code}:${issue.path}`}
                                    severity={issue.severity === 'ERROR' ? 'error' : 'warning'}
                                  >
                                    <strong>{issue.code}</strong>: {issue.message}
                                  </Alert>
                                ))}
                              </Stack>
                            ) : null}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Assistant staging</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                Bring prompt posture, live actions, knowledge coverage, endpoints, and proof-of-concept data into one
                readiness view before customer-visible validation.
              </Typography>
            </Box>

            <Alert severity={blockedChecks.length > 0 ? 'warning' : warningChecks.length > 0 ? 'info' : 'success'}>
              {readinessMessage}
            </Alert>

            <Grid container spacing={2}>
              <Grid item xs={12} md={6} xl={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Prompt posture
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {baselineQuery.data?.versionLabel ?? 'Draft-led'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Draft prompts: {draftPromptCount}. Published prompts: {publishedPromptCount}.
                      </Typography>
                      <Chip
                        label={promptSession?.active ? `POC override active: ${promptSession.promptKeyCount}` : 'POC override inactive'}
                        size="small"
                        color={promptSession?.active ? 'secondary' : 'default'}
                        variant="outlined"
                      />
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} xl={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Live actions
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {actionsCount} actions
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {routingActionsCount} routed action path(s) are present in the current draft.
                      </Typography>
                      <Chip label={`Template: ${workspace.template.name}`} size="small" variant="outlined" />
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} xl={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Knowledge and data
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {entitySpacesCount} spaces
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        {pocWorkspace?.dataset.profileLabel ?? 'Dataset profile unavailable'}
                      </Typography>
                      <Chip label={`Vectors: ${totalVectors}`} size="small" color={totalVectors > 0 ? 'success' : 'default'} variant="outlined" />
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6} xl={3}>
                <Card variant="outlined" sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack spacing={1}>
                      <Typography variant="overline" color="text.secondary">
                        Runtime endpoints
                      </Typography>
                      <Typography variant="h6" sx={{ fontWeight: 800 }}>
                        {workspace.deployment.runtimeBaseUrl ? 'Applied' : 'Pending apply'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Runtime URL should exist before external UI integration. Connector remains an internal/operator surface.
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={runtimeSwagger ? 'Runtime docs ready' : 'Runtime docs pending'} size="small" variant="outlined" />
                        <Chip label={runtimeAuthOverview ? 'Runtime auth overview ready' : 'Runtime auth overview pending'} size="small" variant="outlined" />
                        <Chip label={connectorAdminOverview ? 'Connector admin via runtime ready' : 'Connector admin via runtime pending'} size="small" variant="outlined" />
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>

            <Grid container spacing={2}>
              {readinessChecks.map((check) => (
                <Grid item xs={12} md={6} xl={4} key={check.key}>
                  <Card variant="outlined" sx={{ height: '100%' }}>
                    <CardContent>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                            {check.label}
                          </Typography>
                          <Chip label={check.status} size="small" color={readinessColor(check.status)} variant="outlined" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {check.message}
                        </Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h6">Production readiness scorecard</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, maxWidth: 980 }}>
                One deployment-level go-live summary covering config completeness, verification evidence,
                security posture, live service health, and operator ownership.
              </Typography>
            </Box>

            {productionReadinessQuery.isLoading ? (
              <Typography color="text.secondary">Loading production readiness scorecard...</Typography>
            ) : productionReadinessQuery.isError ? (
              <Alert severity="error">
                {productionReadinessQuery.error instanceof Error
                  ? productionReadinessQuery.error.message
                  : 'Failed to load production readiness scorecard.'}
              </Alert>
            ) : productionReadinessQuery.data ? (
              <>
                <Alert severity={productionReadinessQuery.data.overallStatus === 'READY'
                  ? 'success'
                  : productionReadinessQuery.data.overallStatus === 'ATTENTION'
                    ? 'warning'
                    : 'error'}
                >
                  {productionReadinessQuery.data.summaryMessage}
                </Alert>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={4}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Go-live posture
                          </Typography>
                          <Typography variant="h4" sx={{ fontWeight: 800 }}>
                            {productionReadinessQuery.data.overallScore}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip
                              label={productionReadinessQuery.data.overallStatus}
                              color={readinessColor(productionReadinessQuery.data.overallStatus)}
                            />
                            <Chip label={`Release: ${productionReadinessQuery.data.latestReleaseStatus}`} variant="outlined" />
                            <Chip label={`Verification: ${productionReadinessQuery.data.latestVerificationStatus}`} variant="outlined" />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            The score is derived from the modeled readiness areas below.
                          </Typography>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} md={8}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Operator ownership
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip
                              label={productionReadinessQuery.data.ownership.status}
                              color={readinessColor(productionReadinessQuery.data.ownership.status)}
                              variant="outlined"
                            />
                            <Chip label={`Assigned: ${productionReadinessQuery.data.ownership.totalAssigned}`} variant="outlined" />
                            <Chip label={`Admins: ${productionReadinessQuery.data.ownership.adminCount}`} variant="outlined" />
                            <Chip label={`Operators: ${productionReadinessQuery.data.ownership.operatorCount}`} variant="outlined" />
                            <Chip label={`Editors: ${productionReadinessQuery.data.ownership.editorCount}`} variant="outlined" />
                            <Chip label={`Viewers: ${productionReadinessQuery.data.ownership.viewerCount}`} variant="outlined" />
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {productionReadinessQuery.data.ownership.message}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Button component={Link} to={buildWorkspacePath('/access')} variant="outlined">
                              Review access
                            </Button>
                            <Button component={Link} to={buildWorkspacePath('/security')} variant="text">
                              Review security
                            </Button>
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>
                </Grid>

                <Grid container spacing={2}>
                  {productionReadinessQuery.data.areas.map((area) => (
                    <Grid item xs={12} md={6} xl={4} key={area.key}>
                      <Card variant="outlined" sx={{ height: '100%' }}>
                        <CardContent>
                          <Stack spacing={1.25}>
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                {area.label}
                              </Typography>
                              <Chip label={area.status} size="small" color={readinessColor(area.status)} variant="outlined" />
                              <Chip label={`Score ${area.score}`} size="small" variant="outlined" />
                            </Stack>
                            <Typography variant="body2" color="text.secondary">
                              {area.message}
                            </Typography>
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  ))}
                </Grid>
              </>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Provider and deployment surface navigation</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                One operator view for provider console links, platform-managed services, client-facing surfaces,
                external dependencies, and the traffic relationships between runtime, connector, artifacts, and upstream APIs.
              </Typography>
            </Box>

            {navigationQuery.isLoading ? (
              <Typography color="text.secondary">Loading service navigation...</Typography>
            ) : navigationQuery.isError ? (
              <Alert severity="error">
                {navigationQuery.error instanceof Error
                  ? navigationQuery.error.message
                  : 'Failed to load deployment service navigation.'}
              </Alert>
            ) : navigationQuery.data ? (
              <>
                <Alert severity={navigationQuery.data.provider.available ? 'success' : 'info'}>
                  {navigationQuery.data.summaryMessage}
                </Alert>

                <Grid container spacing={2}>
                  <Grid item xs={12} lg={4}>
                    <Card variant="outlined" sx={{ height: '100%' }}>
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Typography variant="overline" color="text.secondary">
                            Provider console
                          </Typography>
                          <Typography variant="h6" sx={{ fontWeight: 800 }}>
                            {navigationQuery.data.provider.projectName ?? 'Railway project pending'}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {navigationQuery.data.provider.summaryMessage}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip label={navigationQuery.data.provider.provider} variant="outlined" />
                            <Chip label={navigationQuery.data.provider.mode} variant="outlined" />
                            {navigationQuery.data.provider.workspaceId ? (
                              <Chip label={`Workspace: ${navigationQuery.data.provider.workspaceId}`} variant="outlined" />
                            ) : null}
                          </Stack>
                          <Typography variant="body2">
                            Repository: <strong>{navigationQuery.data.provider.repository}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Branch: <strong>{navigationQuery.data.provider.branch}</strong>
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {navigationQuery.data.provider.projectUrl ? (
                              <Button href={navigationQuery.data.provider.projectUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                Railway project
                              </Button>
                            ) : null}
                            {workspace.access.canOperate && workspace.deployment.runtimeBaseUrl ? (
                              <Button href={workspace.deployment.runtimeBaseUrl} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                Runtime root
                              </Button>
                            ) : null}
                            {workspace.access.canOperate && runtimeAuthOverview ? (
                              <Button href={runtimeAuthOverview} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                Runtime auth overview
                              </Button>
                            ) : null}
                            {workspace.access.canOperate && connectorAdminOverview ? (
                              <Button href={connectorAdminOverview} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                Connector admin via runtime
                              </Button>
                            ) : null}
                          </Stack>
                        </Stack>
                      </CardContent>
                    </Card>
                  </Grid>

                  <Grid item xs={12} lg={8}>
                    <Grid container spacing={2}>
                      {navigationQuery.data.surfaces.map((surface) => (
                        <Grid item xs={12} md={6} key={surface.key}>
                          <Card variant="outlined" sx={{ height: '100%' }}>
                            <CardContent>
                              <Stack spacing={1.25}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {surface.label}
                                  </Typography>
                                  <Chip
                                    label={surfaceTypeLabel(surface.surfaceType)}
                                    size="small"
                                    variant="outlined"
                                    color={surface.platformManaged ? 'secondary' : 'default'}
                                  />
                                  <Chip
                                    label={surface.available ? 'Live link available' : 'Reference only'}
                                    size="small"
                                    color={surface.available ? 'success' : 'default'}
                                    variant="outlined"
                                  />
                                  {surface.serviceName ? <Chip label={surface.serviceName} size="small" variant="outlined" /> : null}
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                  {surface.purpose}
                                </Typography>
                                <Alert severity={surface.available ? 'success' : 'info'}>
                                  {surface.summaryMessage}
                                </Alert>
                                {surface.rootDir ? (
                                  <Typography variant="body2">
                                    Root dir: <strong>{surface.rootDir}</strong>
                                  </Typography>
                                ) : null}
                                {surface.dockerfilePath ? (
                                  <Typography variant="body2">
                                    Dockerfile: <strong>{surface.dockerfilePath}</strong>
                                  </Typography>
                                ) : null}
                                {surface.primaryUrl ? (
                                  <Typography variant="body2" color="text.secondary">
                                    Base URL: {surface.primaryUrl}
                                  </Typography>
                                ) : null}
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  {surface.primaryUrl ? (
                                    <Button href={surface.primaryUrl} target="_blank" rel="noreferrer" variant="outlined" size="small" startIcon={<LaunchRoundedIcon />}>
                                      {surface.platformManaged ? 'Open service' : 'Open surface'}
                                    </Button>
                                  ) : null}
                                  {surface.docsUrl ? (
                                    <Button href={surface.docsUrl} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                      Swagger
                                    </Button>
                                  ) : null}
                                  {surface.openApiUrl ? (
                                    <Button href={surface.openApiUrl} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                      OpenAPI
                                    </Button>
                                  ) : null}
                                  {surface.adminUrl ? (
                                    <Button href={surface.adminUrl} target="_blank" rel="noreferrer" variant="text" size="small" startIcon={<LaunchRoundedIcon />}>
                                      Admin entry
                                    </Button>
                                  ) : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ))}
                    </Grid>
                  </Grid>
                </Grid>

                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
                    Relationship map
                  </Typography>
                  <Grid container spacing={2}>
                    {navigationQuery.data.relationships.map((relationship) => (
                      <Grid item xs={12} md={6} xl={4} key={relationship.key}>
                        <Card variant="outlined" sx={{ height: '100%' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                <Chip label={relationship.flowType} size="small" variant="outlined" />
                                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                  {relationship.fromLabel} → {relationship.toLabel}
                                </Typography>
                              </Stack>
                              <Typography variant="body2" color="text.secondary">
                                {relationship.summaryMessage}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    ))}
                  </Grid>
                </Box>
              </>
            ) : null}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
