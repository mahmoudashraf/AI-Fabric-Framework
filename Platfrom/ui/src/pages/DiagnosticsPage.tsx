import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import SyncRoundedIcon from '@mui/icons-material/SyncRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  Link,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentActivity,
  executeDeploymentRemediation,
  fetchDeploymentRemediation,
  fetchDeploymentReleases,
  fetchDeploymentRailwayLogs,
  fetchDeploymentSourceOfTruth,
  fetchDeploymentVerificationRuns,
  fetchRailwayPreflight,
  type DeploymentRemediationActionSummary,
  type DeploymentRailwayLiveReadbackSummary,
  type DeploymentRailwayLiveServiceSummary,
  type DeploymentRailwayLogsResponse,
  type PlatformAuditEventSummary,
  type DeploymentReleaseSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

type VerificationCheck = {
  name: string
  status: string
  message: string
  details?: unknown
}

type ProvisioningProgressStep = {
  key: string
  description: string
  status: string
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
}

type FailureAnalysis = {
  severity: 'success' | 'info' | 'warning' | 'error'
  stage: string
  headline: string
  reason: string
  currentStep: string
  recommendation: string
}

type RecoveryHint = {
  key: string
  severity: 'info' | 'warning' | 'error'
  title: string
  message: string
  service: 'runtime' | 'restConnector' | 'vectorizationRunner'
  source: 'deployment' | 'build' | 'http'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? formatTimestamp(value) : '—'
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

function swaggerUiUrl(baseUrl: string | null | undefined): string | null {
  if (!baseUrl || baseUrl.trim().length === 0) {
    return null
  }
  return `${baseUrl.replace(/\/$/, '')}/swagger-ui/index.html`
}

function summarizeProvisioningDetails(value: unknown) {
  if (!isRecord(value)) {
    return {
      provider: 'Unknown',
      projectId: null as string | null,
      projectUrl: null as string | null,
      projectName: 'Unknown',
      repository: 'Unknown',
      branch: 'Unknown',
      artifactStrategy: 'Unknown',
      generatedAt: 'Unknown',
      runtimeRootDir: 'Unknown',
      runtimeDockerfilePath: 'Unknown',
      connectorRootDir: 'Unknown',
      connectorDockerfilePath: 'Unknown',
      artifactUrls: {
        actions: 'Unknown',
        entities: 'Unknown',
        routing: 'Unknown',
        manifest: 'Unknown',
      },
      progress: {
        currentStepKey: 'Unknown',
        currentStepDescription: 'No progress recorded',
        currentStepStatus: 'UNKNOWN',
        errorMessage: null,
        steps: [] as ProvisioningProgressStep[],
      },
    }
  }

  const services = isRecord(value.services) ? value.services : {}
  const runtime = isRecord(services.runtime) ? services.runtime : {}
  const restConnector = isRecord(services.restConnector) ? services.restConnector : {}
  const artifactUrls = isRecord(value.artifactUrls) ? value.artifactUrls : {}
  const progressNode = isRecord(value.progress) ? value.progress : {}
  const stepNodes = Array.isArray(progressNode.steps) ? progressNode.steps : []

  return {
    provider: typeof value.provider === 'string' ? value.provider : 'Unknown',
    projectId: typeof value.projectId === 'string'
      ? value.projectId
      : isRecord(value.railway) && typeof value.railway.projectId === 'string'
        ? value.railway.projectId
        : null,
    projectUrl:
      typeof value.projectId === 'string'
        ? `https://railway.com/project/${value.projectId}`
        : isRecord(value.railway) && typeof value.railway.projectId === 'string'
          ? `https://railway.com/project/${value.railway.projectId}`
          : null,
    projectName: typeof value.projectName === 'string' ? value.projectName : 'Unknown',
    repository: typeof value.repository === 'string' ? value.repository : 'Unknown',
    branch: typeof value.branch === 'string' ? value.branch : 'Unknown',
    artifactStrategy: typeof value.artifactStrategy === 'string' ? value.artifactStrategy : 'Unknown',
    generatedAt: typeof value.generatedAt === 'string' ? value.generatedAt : 'Unknown',
    runtimeRootDir: typeof runtime.rootDir === 'string' ? runtime.rootDir : 'Unknown',
    runtimeDockerfilePath: typeof runtime.dockerfilePath === 'string' ? runtime.dockerfilePath : 'Unknown',
    connectorRootDir: typeof restConnector.rootDir === 'string' ? restConnector.rootDir : 'Unknown',
    connectorDockerfilePath:
      typeof restConnector.dockerfilePath === 'string' ? restConnector.dockerfilePath : 'Unknown',
    artifactUrls: {
      actions: typeof artifactUrls.actions === 'string' ? artifactUrls.actions : 'Unknown',
      entities: typeof artifactUrls.entities === 'string' ? artifactUrls.entities : 'Unknown',
      routing: typeof artifactUrls.routing === 'string' ? artifactUrls.routing : 'Unknown',
      prompts: typeof artifactUrls.prompts === 'string' ? artifactUrls.prompts : 'Unknown',
      manifest: typeof artifactUrls.manifest === 'string' ? artifactUrls.manifest : 'Unknown',
    },
    progress: {
      currentStepKey:
        typeof progressNode.currentStepKey === 'string' ? progressNode.currentStepKey : 'Unknown',
      currentStepDescription:
        typeof progressNode.currentStepDescription === 'string'
          ? progressNode.currentStepDescription
          : 'No progress recorded',
      currentStepStatus:
        typeof progressNode.currentStepStatus === 'string' ? progressNode.currentStepStatus : 'UNKNOWN',
      errorMessage:
        typeof progressNode.errorMessage === 'string' ? progressNode.errorMessage : null,
      steps: stepNodes.flatMap((step): ProvisioningProgressStep[] => {
        if (!isRecord(step)) {
          return []
        }
        return [{
          key: typeof step.key === 'string' ? step.key : 'unknown_step',
          description: typeof step.description === 'string' ? step.description : 'No description',
          status: typeof step.status === 'string' ? step.status : 'UNKNOWN',
          startedAt: typeof step.startedAt === 'string' ? step.startedAt : null,
          completedAt: typeof step.completedAt === 'string' ? step.completedAt : null,
          errorMessage: typeof step.errorMessage === 'string' ? step.errorMessage : null,
        }]
      }),
    },
  }
}

function readChecks(value: unknown): VerificationCheck[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value.flatMap((item) => {
    if (!isRecord(item)) {
      return []
    }

    const name = typeof item.name === 'string' ? item.name : 'unknown_check'
    const status = typeof item.status === 'string' ? item.status : 'UNKNOWN'
    const message = typeof item.message === 'string' ? item.message : 'No message'
    return [{ name, status, message, details: item.details }]
  })
}

function formatDetails(value: unknown): string {
  if (value == null) {
    return '—'
  }

  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function statusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
  if (status === 'PASSED') {
    return 'success'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'SKIPPED') {
    return 'default'
  }
  return 'error'
}

function releaseStatusColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'APPLIED_VERIFIED') {
    return 'success'
  }
  if (status === 'APPLY_REQUESTED' || status === 'PRE_APPLY_VERIFYING' || status === 'PROVISIONING' || status === 'VERIFYING') {
    return 'info'
  }
  if (status === 'APPLIED_VERIFICATION_FAILED') {
    return 'warning'
  }
  if (status === 'PRE_APPLY_BLOCKED' || status === 'FAILED') {
    return 'error'
  }
  return 'default'
}

function releaseSignalColor(status: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'PASSED' || status === 'SUCCEEDED' || status === 'PLANNED' || status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'AWAITING_CONFIRMATION') {
    return 'warning'
  }
  if (status === 'RUNNING' || status === 'QUEUED' || status === 'PENDING') {
    return 'info'
  }
  if (status === 'SKIPPED') {
    return 'default'
  }
  if (status === 'FAILED') {
    return 'error'
  }
  return 'warning'
}

function remediationSeverityColor(severity: string): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (severity === 'LOW') {
    return 'info'
  }
  if (severity === 'WARNING') {
    return 'warning'
  }
  if (severity === 'ERROR') {
    return 'error'
  }
  return 'default'
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

function serviceStatusColor(status: string): 'success' | 'warning' | 'error' | 'default' {
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

function summarizeEnvDrift(
  service: DeploymentRailwayLiveServiceSummary,
  state: 'MISSING' | 'MISMATCHED',
): string {
  const keys = service.envVars
    .filter((item) => item.driftState === state)
    .map((item) => item.key)
  return keys.length > 0 ? keys.join(', ') : 'None'
}

function driftedServices(readback: DeploymentRailwayLiveReadbackSummary | null | undefined): DeploymentRailwayLiveServiceSummary[] {
  if (!readback?.available) {
    return []
  }
  return [readback.runtime, readback.restConnector, readback.vectorizationRunner]
    .filter((service): service is DeploymentRailwayLiveServiceSummary => Boolean(service))
    .filter((service) => service.status === 'WARNING')
}

function isReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

function summarizeAuditDetails(value: unknown): string {
  if (value == null) {
    return '—'
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function summarizeLogAttributes(attributes: DeploymentRailwayLogsResponse['entries'][number]['attributes']): string {
  if (!Array.isArray(attributes) || attributes.length === 0) {
    return '—'
  }
  return attributes
    .map((attribute) => `${attribute.key ?? 'unknown'}=${attribute.value ?? ''}`)
    .join(', ')
}

function deriveFailureAnalysis(
  latestRelease: DeploymentReleaseSummary | null,
  provisioningSummary: ReturnType<typeof summarizeProvisioningDetails>,
  selectedRun: { status?: string; summaryMessage?: string } | null,
  selectedRunChecks: VerificationCheck[],
  liveRailwayReadback: DeploymentRailwayLiveReadbackSummary | null,
): FailureAnalysis {
  const verificationRun = selectedRun
  const failedProvisioningStep = provisioningSummary.progress.steps.find((step) => step.status === 'FAILED')
  const failedVerificationCheck = selectedRunChecks.find((check) => check.status === 'FAILED')
  const releaseError = latestRelease?.errorMessage ?? provisioningSummary.progress.errorMessage ?? failedProvisioningStep?.errorMessage ?? null
  const activationUnconfirmed = latestRelease?.status === 'PROVISIONING'
    && latestRelease.provisioningStatus === 'AWAITING_CONFIRMATION'

  if (!latestRelease) {
    return {
      severity: 'info',
      stage: 'No release',
      headline: 'No deployment release exists yet.',
      reason: 'Apply a version before expecting runtime evidence, Railway logs, or verification history.',
      currentStep: 'Release not started',
      recommendation: 'Publish and apply a version, then return here for operational evidence.',
    }
  }

  if (activationUnconfirmed) {
    return {
      severity: 'warning',
      stage: 'Activation not confirmed',
      headline: 'Railway is still taking longer than expected to report a healthy active deployment.',
      reason: latestRelease.currentStepDescription ?? 'Deployment activation status is not confirmed yet.',
      currentStep: provisioningSummary.progress.currentStepDescription,
      recommendation: 'Refresh the release status or compare Railway logs before retrying apply.',
    }
  }

  if (releaseError) {
    return {
      severity: 'error',
      stage: latestRelease.verificationStatus === 'FAILED'
        || latestRelease.status === 'APPLIED_VERIFICATION_FAILED'
        || latestRelease.status === 'PRE_APPLY_BLOCKED'
        ? 'Verification failure'
        : 'Provisioning failure',
      headline: 'Latest release failed before reaching a healthy verified state.',
      reason: releaseError,
      currentStep: failedProvisioningStep?.description ?? latestRelease.currentStepDescription ?? 'Unknown step',
      recommendation: recoveryRecommendation(releaseError),
    }
  }

  if (failedVerificationCheck) {
    return {
      severity: 'warning',
      stage: 'Verification check failed',
      headline: 'Latest verification run contains at least one failing check.',
      reason: failedVerificationCheck.message,
      currentStep: failedVerificationCheck.name,
      recommendation: recoveryRecommendation(failedVerificationCheck.message),
    }
  }

  if (liveRailwayReadback?.available && liveRailwayReadback.status === 'WARNING') {
    const drifted = driftedServices(liveRailwayReadback)
    const driftedLabels = drifted.map((service) => service.label).join(' and ')
    return {
      severity: 'warning',
      stage: 'Provider drift',
      headline: 'Railway live state has drifted away from the platform-managed deployment.',
      reason: liveRailwayReadback.summaryMessage,
      currentStep: driftedLabels.length > 0 ? driftedLabels : 'Railway live read-back',
      recommendation: 'Redeploy the active version before using provider-side restarts or runtime data resets.',
    }
  }

  if (latestRelease.status === 'APPLY_REQUESTED'
    || latestRelease.status === 'PRE_APPLY_VERIFYING'
    || latestRelease.status === 'PROVISIONING'
    || latestRelease.status === 'VERIFYING') {
    return {
      severity: 'info',
      stage: 'Rollout in progress',
      headline: 'Release is still moving through provisioning or verification.',
      reason: latestRelease.currentStepDescription ?? 'The rollout is still progressing.',
      currentStep: provisioningSummary.progress.currentStepDescription,
      recommendation: 'Use the release timeline and live logs to verify the rollout completes cleanly.',
    }
  }

  if (verificationRun?.status === 'PASSED' || latestRelease.status === 'APPLIED_VERIFIED') {
    return {
      severity: 'success',
      stage: 'Healthy',
      headline: 'Latest release and verification evidence are healthy.',
      reason: latestRelease.currentStepDescription ?? verificationRun?.summaryMessage ?? 'Deployment is verified.',
      currentStep: provisioningSummary.progress.currentStepDescription,
      recommendation: 'Keep this as the current reference point when comparing future rollout regressions.',
    }
  }

  return {
    severity: 'warning',
    stage: 'Needs review',
    headline: 'Latest release evidence is incomplete or inconsistent.',
    reason: latestRelease.currentStepDescription ?? verificationRun?.summaryMessage ?? 'Review the latest release and verification history.',
    currentStep: provisioningSummary.progress.currentStepDescription,
    recommendation: 'Use log shortcuts and verification details to complete the diagnosis.',
  }
}

function recoveryRecommendation(reason: string): string {
  const normalized = reason.toLowerCase()
  if (normalized.includes('railway_workspace_id')) {
    return 'Configure the Railway workspace id and API token in platform provisioning before retrying apply.'
  }
  if (normalized.includes('routing config not found') || normalized.includes('artifact')) {
    return 'Publish a fresh version and confirm the artifact delivery base URL and routing artifact are reachable.'
  }
  if (normalized.includes('unauthorized') || normalized.includes('401')) {
    return 'Reconcile admin and connector API keys, then re-apply so runtime and connector use the same expected secrets.'
  }
  if (normalized.includes('runtime service is unavailable') || normalized.includes('503')) {
    return 'Check runtime rollout health first, then confirm the connector proxy points at the active runtime base URL.'
  }
  if (normalized.includes('embedding generation failed') || normalized.includes('openai_api_key')) {
    return 'Verify provider credentials in platform secrets and re-apply before rerunning indexing or verification.'
  }
  if (normalized.includes('timeout')) {
    return 'Review build and deployment logs for slow or blocked upstream calls before expanding timeout budgets.'
  }
  return 'Start with the recommended logs below, then compare release, verification, and artifact provenance before retrying.'
}

function deriveRecoveryHints(
  analysis: FailureAnalysis,
  latestRelease: DeploymentReleaseSummary | null,
  liveRailwayReadback: DeploymentRailwayLiveReadbackSummary | null,
): RecoveryHint[] {
  const text = `${analysis.reason} ${analysis.currentStep} ${latestRelease?.status ?? ''}`.toLowerCase()
  const hints: RecoveryHint[] = []

  if (liveRailwayReadback?.available && liveRailwayReadback.status === 'WARNING') {
    driftedServices(liveRailwayReadback).forEach((service) => {
      hints.push({
        key: `provider-drift-${service.key}`,
        severity: 'warning',
        title: `Reconcile ${service.label.toLowerCase()} drift before targeted recovery`,
        message: 'Redeploy the active version first. Provider-side restart is intentionally blocked while Railway live state differs from the platform-managed plan.',
        service: service.key === 'restConnector'
          ? 'restConnector'
          : service.key === 'vectorizationRunner'
            ? 'vectorizationRunner'
            : 'runtime',
        source: 'deployment',
      })
    })
  }

  if (text.includes('routing config not found') || text.includes('artifact')) {
    hints.push({
      key: 'artifacts',
      severity: 'error',
      title: 'Check connector deployment logs for artifact fetch failures',
      message: 'Connector startup or runtime fetch failures usually show up in deployment logs first.',
      service: 'restConnector',
      source: 'deployment',
    })
  }
  if (text.includes('unauthorized') || text.includes('401')) {
    hints.push({
      key: 'auth',
      severity: 'error',
      title: 'Inspect runtime and connector auth failures',
      message: 'HTTP logs help confirm which header or secret mismatch caused the request rejection.',
      service: 'restConnector',
      source: 'http',
    })
  }
  if (text.includes('runtime service is unavailable') || text.includes('503')) {
    hints.push({
      key: 'runtime-unavailable',
      severity: 'error',
      title: 'Verify runtime deployment health first',
      message: 'Connector proxy failures are usually downstream of a runtime rollout or activation problem.',
      service: 'runtime',
      source: 'deployment',
    })
  }
  if (text.includes('embedding generation failed') || text.includes('openai_api_key')) {
    hints.push({
      key: 'provider',
      severity: 'warning',
      title: 'Confirm provider credentials and runtime env',
      message: 'Deployment logs and verification output should confirm whether the runtime saw the expected provider key.',
      service: 'runtime',
      source: 'deployment',
    })
  }
  if (text.includes('build') || text.includes('dockerfile')) {
    hints.push({
      key: 'build',
      severity: 'warning',
      title: 'Review build logs for container assembly errors',
      message: 'Build-time failures often occur before deployment logs become useful.',
      service: 'runtime',
      source: 'build',
    })
  }

  if (hints.length === 0) {
    hints.push({
      key: 'generic',
      severity: analysis.severity === 'error' ? 'error' : 'warning',
      title: 'Start with deployment logs for the latest release',
      message: 'Deployment logs usually show the first actionable failure signal before downstream checks diverge.',
      service: 'runtime',
      source: 'deployment',
    })
  }

  return hints
}

export function DiagnosticsPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [selectedRunId, setSelectedRunId] = useState('')
  const [selectedLogService, setSelectedLogService] = useState<'runtime' | 'restConnector' | 'vectorizationRunner'>('runtime')
  const [selectedLogSource, setSelectedLogSource] = useState('deployment')
  const [selectedRemediationAction, setSelectedRemediationAction] = useState<DeploymentRemediationActionSummary | null>(null)
  const [remediationConfirmed, setRemediationConfirmed] = useState(false)
  const [remediationReason, setRemediationReason] = useState('')
  const [remediationApprovalId, setRemediationApprovalId] = useState('')

  const railwayPreflightQuery = useQuery({
    queryKey: ['railway-preflight'],
    queryFn: fetchRailwayPreflight,
  })

  const auditEventsQuery = useQuery({
    queryKey: ['deployment-activity', selectedDeploymentId],
    queryFn: () => fetchDeploymentActivity(selectedDeploymentId, 50),
    enabled: selectedDeploymentId.length > 0,
  })

  const selectedDeployment = useMemo(
    () => workspace?.deployment ?? selectedDeploymentSummary ?? null,
    [selectedDeploymentSummary, workspace],
  )

  const releasesQuery = useQuery({
    queryKey: ['deployment-releases', selectedDeploymentId],
    queryFn: () => fetchDeploymentReleases(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: (query) => {
      const releases = (query.state.data as DeploymentReleaseSummary[] | undefined) ?? []
      return releases.some(isReleaseInProgress) ? 3000 : false
    },
  })

  const releaseHistory = releasesQuery.data ?? []
  const latestRelease = releaseHistory[0] ?? null
  const releasesInProgress = releaseHistory.some(isReleaseInProgress)

  const verificationRunsQuery = useQuery({
    queryKey: ['deployment-verification-runs', selectedDeploymentId],
    queryFn: () => fetchDeploymentVerificationRuns(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: releasesInProgress ? 3000 : false,
  })

  const railwayLogsQuery = useQuery({
    queryKey: [
      'deployment-railway-logs',
      selectedDeploymentId,
      latestRelease?.id ?? null,
      selectedLogService,
      selectedLogSource,
    ],
    queryFn: () => fetchDeploymentRailwayLogs(selectedDeploymentId, {
      releaseId: latestRelease?.id ?? undefined,
      service: selectedLogService,
      source: selectedLogSource,
      limit: 150,
    }),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: releasesInProgress ? 5000 : false,
  })

  const remediationQuery = useQuery({
    queryKey: ['deployment-remediation', selectedDeploymentId],
    queryFn: () => fetchDeploymentRemediation(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const sourceOfTruthQuery = useQuery({
    queryKey: ['deployment-source-of-truth', selectedDeploymentId],
    queryFn: () => fetchDeploymentSourceOfTruth(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    refetchInterval: releasesInProgress ? 3000 : false,
  })

  const remediationMutation = useMutation({
    mutationFn: (payload: { actionKey: string; confirm?: boolean; reason?: string; approvalId?: string }) =>
      executeDeploymentRemediation(selectedDeploymentId, payload.actionKey, payload),
    onSuccess: async (result) => {
      if (result.referenceType === 'DEPLOYMENT_VERIFICATION_RUN') {
        setSelectedRunId(result.referenceId)
      }
      setSelectedRemediationAction(null)
      setRemediationConfirmed(false)
      setRemediationReason('')
      setRemediationApprovalId('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-remediation', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-source-of-truth', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-activity', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-railway-logs'] }),
      ])
    },
  })

  const verificationRuns = verificationRunsQuery.data ?? []
  const provisioningSummary = summarizeProvisioningDetails(latestRelease?.provisioningDetails)
  const railwayLogs = railwayLogsQuery.data
  const liveRailwayReadback = sourceOfTruthQuery.data?.liveRailwayReadback ?? null
  const managedVectorState = sourceOfTruthQuery.data?.managedVector ?? null
  const tenantScopedVector = sourceOfTruthQuery.data?.tenantScopedVector ?? null
  const railwayProjectUrl = liveRailwayReadback?.projectId
    ? `https://railway.com/project/${liveRailwayReadback.projectId}`
    : provisioningSummary.projectUrl

  useEffect(() => {
    if (!releasesInProgress) {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: ['deployments'] })
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [queryClient, releasesInProgress])

  useEffect(() => {
    if (verificationRuns.length === 0) {
      if (selectedRunId !== '') {
        setSelectedRunId('')
      }
      return
    }

    if (!verificationRuns.some((run) => run.id === selectedRunId)) {
      setSelectedRunId(verificationRuns[0].id)
    }
  }, [selectedRunId, verificationRuns])

  const selectedRun = useMemo(
    () => verificationRuns.find((run) => run.id === selectedRunId) ?? verificationRuns[0] ?? null,
    [selectedRunId, verificationRuns],
  )
  const selectedRunChecks = readChecks(selectedRun?.checks)
  const auditEvents = auditEventsQuery.data ?? []
  const failureAnalysis = deriveFailureAnalysis(
    latestRelease,
    provisioningSummary,
    selectedRun,
    selectedRunChecks,
    liveRailwayReadback,
  )
  const recoveryHints = deriveRecoveryHints(failureAnalysis, latestRelease, liveRailwayReadback)

  const focusLogs = (service: 'runtime' | 'restConnector' | 'vectorizationRunner', source: 'deployment' | 'build' | 'http') => {
    setSelectedLogService(service)
    setSelectedLogSource(source)
    window.requestAnimationFrame(() => {
      document.getElementById('railway-logs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Diagnostics" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Release evidence and live verification
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          This screen combines deployment endpoints, provisioning evidence, stored verification runs,
          and manual reruns so the platform can act as an operator console instead of only a config editor.
        </Typography>
        {workspace?.deployment.deletion ? (
          <Alert
            severity={workspace.deployment.deletion.status === 'FAILED' ? 'error' : 'info'}
            sx={{ mt: 2 }}
          >
            <strong>Deletion status</strong>: {workspace.deployment.deletion.message}
          </Alert>
        ) : null}
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h6">Railway preflight</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  Read-only provisioning readiness check for Railway API mode, workspace access, public artifact delivery,
                  and required platform secrets.
                </Typography>
              </Box>
              <Button
                variant="outlined"
                startIcon={<SyncRoundedIcon />}
                onClick={() => queryClient.invalidateQueries({ queryKey: ['railway-preflight'] })}
                disabled={railwayPreflightQuery.isFetching}
              >
                {railwayPreflightQuery.isFetching ? 'Refreshing...' : 'Refresh preflight'}
              </Button>
            </Stack>

            {railwayPreflightQuery.isLoading ? (
              <Typography color="text.secondary">Running Railway preflight...</Typography>
            ) : railwayPreflightQuery.isError ? (
              <Alert severity="error">
                {railwayPreflightQuery.error instanceof Error
                  ? railwayPreflightQuery.error.message
                  : 'Failed to run Railway preflight'}
              </Alert>
            ) : railwayPreflightQuery.data ? (
              <>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip
                    label={railwayPreflightQuery.data.ready ? 'Ready to provision' : 'Not ready to provision'}
                    color={railwayPreflightQuery.data.ready ? 'success' : 'warning'}
                  />
                  <Chip label={`Mode: ${railwayPreflightQuery.data.mode}`} variant="outlined" />
                  <Chip
                    label={`Workspace: ${railwayPreflightQuery.data.workspaceName ?? railwayPreflightQuery.data.workspaceId ?? 'Unresolved'}`}
                    variant="outlined"
                  />
                  <Chip
                    label={`Checked: ${formatTimestamp(railwayPreflightQuery.data.checkedAt)}`}
                    variant="outlined"
                  />
                </Stack>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <Stack spacing={1}>
                      <Typography variant="body2">
                        Repository: <strong>{railwayPreflightQuery.data.repository}</strong>
                      </Typography>
                      <Typography variant="body2">
                        Branch: <strong>{railwayPreflightQuery.data.branch}</strong>
                      </Typography>
                      <Typography variant="body2">
                        Public base URL: <strong>{railwayPreflightQuery.data.publicBaseUrl}</strong>
                      </Typography>
                    </Stack>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Check</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Message</TableCell>
                          <TableCell>Details</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {railwayPreflightQuery.data.checks.map((check) => (
                          <TableRow key={check.key} hover>
                            <TableCell sx={{ fontFamily: 'monospace' }}>{check.key}</TableCell>
                            <TableCell>
                              <Chip size="small" color={statusColor(check.status)} label={check.status} />
                            </TableCell>
                            <TableCell>{check.message}</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', maxWidth: 320 }}>
                              {check.details ?? '—'}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </Grid>
                </Grid>
              </>
            ) : null}

            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Diagnostics now stay anchored to the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ flex: 1 }}>
                {workspace ? (
                  <>
                    <Chip label={workspace.deployment.name} variant="outlined" />
                    <Chip label={workspace.deployment.environment} variant="outlined" />
                    <Chip label={workspace.template.name} variant="outlined" />
                  </>
                ) : (
                  <Chip label="No deployment selected" variant="outlined" />
                )}
              </Stack>

              <Button
                variant="outlined"
                startIcon={<SyncRoundedIcon />}
                disabled={!selectedDeploymentId || railwayLogsQuery.isFetching}
                onClick={() => queryClient.invalidateQueries({
                  queryKey: [
                    'deployment-railway-logs',
                    selectedDeploymentId,
                    latestRelease?.id ?? null,
                    selectedLogService,
                    selectedLogSource,
                  ],
                })}
              >
                {railwayLogsQuery.isFetching ? 'Refreshing logs...' : 'Refresh logs'}
              </Button>
            </Stack>

            {remediationMutation.isError ? (
              <Alert severity="error">
                {remediationMutation.error instanceof Error
                  ? remediationMutation.error.message
                  : 'Remediation action failed'}
              </Alert>
            ) : null}

            {remediationMutation.isSuccess ? (
              <Alert severity={remediationMutation.data.status === 'COMPLETED' ? 'success' : 'info'}>
                {remediationMutation.data.message}
              </Alert>
            ) : null}

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion ?? '—'}`} variant="outlined" />
                <Chip label={workspace?.template.name ?? selectedDeployment.templateId} variant="outlined" />
                {releasesInProgress ? (
                  <Chip
                    label={`Release running: ${latestRelease?.currentStepDescription ?? latestRelease?.status ?? 'In progress'}`}
                    color="info"
                  />
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to inspect diagnostics.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={5}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Failure analysis</Typography>
                    <Alert severity={failureAnalysis.severity}>
                      <strong>{failureAnalysis.stage}</strong>: {failureAnalysis.headline}
                    </Alert>
                    <Typography variant="body2">
                      Reason: <strong>{failureAnalysis.reason}</strong>
                    </Typography>
                    <Typography variant="body2">
                      Current step: <strong>{failureAnalysis.currentStep}</strong>
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {failureAnalysis.recommendation}
                    </Typography>
                    {latestRelease ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={latestRelease.status} color={releaseStatusColor(latestRelease.status)} />
                        <Chip label={latestRelease.provisioningStatus} color={releaseSignalColor(latestRelease.provisioningStatus)} variant="outlined" />
                        <Chip label={latestRelease.verificationStatus} color={releaseSignalColor(latestRelease.verificationStatus)} variant="outlined" />
                      </Stack>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Known recovery hints</Typography>
                    {recoveryHints.map((hint) => (
                      <Alert key={hint.key} severity={hint.severity}>
                        <strong>{hint.title}</strong>
                        <br />
                        {hint.message}
                      </Alert>
                    ))}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={3}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Log shortcuts</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Jump the log viewer to the most likely evidence source for the current failure pattern.
                    </Typography>
                    {recoveryHints.map((hint) => (
                      <Button
                        key={`log-${hint.key}`}
                        variant="outlined"
                        onClick={() => focusLogs(hint.service, hint.source)}
                      >
                        {hint.service} {hint.source} logs
                      </Button>
                    ))}
                    {railwayProjectUrl ? (
                      <Button
                        href={railwayProjectUrl}
                        target="_blank"
                        rel="noreferrer"
                        variant="text"
                      >
                        Open Railway project
                      </Button>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="h6">Railway live drift</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Live provider read-back from the deployment source of truth. This is the safety signal used to
                      block targeted restarts when Railway settings drift away from the platform-managed plan.
                    </Typography>
                  </Box>
                  <Button
                    variant="outlined"
                    startIcon={<SyncRoundedIcon />}
                    onClick={() => queryClient.invalidateQueries({ queryKey: ['deployment-source-of-truth', selectedDeploymentId] })}
                    disabled={sourceOfTruthQuery.isFetching}
                  >
                    {sourceOfTruthQuery.isFetching ? 'Refreshing live state...' : 'Refresh live state'}
                  </Button>
                </Stack>

                {sourceOfTruthQuery.isLoading ? (
                  <Typography color="text.secondary">Loading Railway live read-back...</Typography>
                ) : sourceOfTruthQuery.isError ? (
                  <Alert severity="error">
                    {sourceOfTruthQuery.error instanceof Error
                      ? sourceOfTruthQuery.error.message
                      : 'Failed to load Railway live read-back.'}
                  </Alert>
                ) : liveRailwayReadback ? (
                  <>
                    <Alert severity={alertSeverityForStatus(liveRailwayReadback.status, liveRailwayReadback.available)}>
                      {liveRailwayReadback.summaryMessage}
                    </Alert>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Project: ${liveRailwayReadback.projectName ?? 'Unavailable'}`} variant="outlined" />
                      <Chip label={`Environment: ${liveRailwayReadback.environmentName ?? 'Unavailable'}`} variant="outlined" />
                      <Chip
                        label={`Status: ${liveRailwayReadback.status}`}
                        color={serviceStatusColor(liveRailwayReadback.status)}
                      />
                    </Stack>

                    {railwayProjectUrl ? (
                      <Box>
                        <Button href={railwayProjectUrl} target="_blank" rel="noreferrer" variant="text">
                          Open Railway project
                        </Button>
                      </Box>
                    ) : null}

                    {liveRailwayReadback.available ? (
                      <Grid container spacing={2}>
                        {[liveRailwayReadback.runtime, liveRailwayReadback.restConnector, liveRailwayReadback.vectorizationRunner]
                          .filter((service): service is DeploymentRailwayLiveServiceSummary => Boolean(service))
                          .map((service) => (
                          <Grid item xs={12} md={6} key={`drift-${service.key}`}>
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
                    ) : null}
                  </>
                ) : (
                  <Alert severity="info">No live Railway read-back is available for this deployment yet.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Tenant-scoped vector scope</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Provider-native tenant handle resolution for the current deployment binding. This is the operator
                    view for shared vector posture, lifecycle ownership, and migration safety.
                  </Typography>
                </Box>

                {sourceOfTruthQuery.isLoading ? (
                  <Typography color="text.secondary">Loading tenant-scoped vector scope...</Typography>
                ) : sourceOfTruthQuery.isError ? (
                  <Alert severity="error">
                    {sourceOfTruthQuery.error instanceof Error
                      ? sourceOfTruthQuery.error.message
                      : 'Failed to load tenant-scoped vector scope.'}
                  </Alert>
                ) : tenantScopedVector ? (
                  <>
                    <Alert severity={alertSeverityForStatus(tenantScopedVector.status, true)}>
                      {tenantScopedVector.summaryMessage}
                    </Alert>
                    {tenantScopedVector.registry ? (
                      <Alert severity={alertSeverityForStatus(tenantScopedVector.registry.status, true)}>
                        {tenantScopedVector.registry.message}
                      </Alert>
                    ) : null}
                    {tenantScopedVector.registry ? (
                      <Alert severity={alertSeverityForStatus(tenantScopedVector.registry.cleanupReadinessStatus, true)}>
                        {tenantScopedVector.registry.cleanupReadinessMessage}
                      </Alert>
                    ) : null}

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Posture: ${tenantScopedVector.vectorStoragePosture}`} variant="outlined" />
                      <Chip label={`Strategy: ${tenantScopedVector.vectorStrategy}`} variant="outlined" />
                      <Chip label={`Provisioning: ${tenantScopedVector.vectorProvisioningMode}`} variant="outlined" />
                      <Chip label={`Lifecycle: ${tenantScopedVector.lifecycleOwner}`} variant="outlined" />
                      <Chip label={`Scope: ${tenantScopedVector.scopeType}`} color={serviceStatusColor(tenantScopedVector.status)} />
                      {tenantScopedVector.registry ? (
                        <Chip
                          label={`Registry: ${tenantScopedVector.registry.status}`}
                          color={serviceStatusColor(tenantScopedVector.registry.status)}
                          variant="outlined"
                        />
                      ) : null}
                      {tenantScopedVector.registry ? (
                        <Chip
                          label={`Cleanup: ${tenantScopedVector.registry.cleanupReadinessStatus}`}
                          color={serviceStatusColor(tenantScopedVector.registry.cleanupReadinessStatus)}
                          variant="outlined"
                        />
                      ) : null}
                    </Stack>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined" sx={{ height: '100%' }}>
                          <CardContent>
                            <Stack spacing={1.25}>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Binding
                              </Typography>
                              <Typography variant="body2">
                                Customer: <strong>{tenantScopedVector.customerName}</strong>
                              </Typography>
                              <Typography variant="body2">
                                Tenant: <strong>{tenantScopedVector.tenantName}</strong>
                              </Typography>
                              <Typography
                                variant="body2"
                                color={tenantScopedVector.migrationLocked ? 'warning.main' : 'text.secondary'}
                              >
                                {tenantScopedVector.migrationMessage}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card variant="outlined" sx={{ height: '100%' }}>
                          <CardContent>
                            <Stack spacing={1.25}>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Provider handle
                              </Typography>
                              {tenantScopedVector.rootResourceLabel && tenantScopedVector.rootResourceValue ? (
                                <Typography variant="body2">
                                  {tenantScopedVector.rootResourceLabel}: <strong>{tenantScopedVector.rootResourceValue}</strong>
                                </Typography>
                              ) : null}
                              {tenantScopedVector.scopePrefix ? (
                                <Typography variant="body2">
                                  Scope prefix: <strong>{tenantScopedVector.scopePrefix}</strong>
                                </Typography>
                              ) : null}
                              {tenantScopedVector.tenantHandle ? (
                                <Typography variant="body2">
                                  Tenant handle: <strong>{tenantScopedVector.tenantHandle}</strong>
                                </Typography>
                              ) : null}
                              {tenantScopedVector.scopePattern ? (
                                <Typography variant="body2">
                                  Scope pattern: <strong>{tenantScopedVector.scopePattern}</strong>
                                </Typography>
                              ) : null}
                              <Typography variant="body2" color="text.secondary">
                                {tenantScopedVector.backupRestorePosture}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      {tenantScopedVector.registry ? (
                        <Grid item xs={12}>
                          <Card variant="outlined">
                            <CardContent>
                              <Stack spacing={1.25}>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                  Registry state
                                </Typography>
                                <Typography variant="body2">
                                  Active records: <strong>{tenantScopedVector.registry.activeRecordCount}</strong>
                                </Typography>
                                <Typography variant="body2">
                                  Historical records: <strong>{tenantScopedVector.registry.historicalRecordCount}</strong>
                                </Typography>
                                {tenantScopedVector.registry.recordId ? (
                                  <Typography variant="body2">
                                    Registry record: <strong>{tenantScopedVector.registry.recordId}</strong>
                                  </Typography>
                                ) : null}
                                {tenantScopedVector.registry.lastUpdatedAt ? (
                                  <Typography variant="body2">
                                    Last updated: <strong>{new Date(tenantScopedVector.registry.lastUpdatedAt).toLocaleString()}</strong>
                                  </Typography>
                                ) : null}
                                <Typography variant="body2">
                                  Cleanup readiness: <strong>{tenantScopedVector.registry.cleanupReadinessStatus}</strong>
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {tenantScopedVector.registry.cleanupReadinessMessage}
                                </Typography>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ) : null}
                    </Grid>
                  </>
                ) : (
                  <Alert severity="info">No tenant-scoped vector scope is available for this deployment yet.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Managed vector resources</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Provider-side indexes or collections that the platform created or reconciled for this deployment.
                  </Typography>
                </Box>

                {sourceOfTruthQuery.isLoading ? (
                  <Typography color="text.secondary">Loading managed vector resource state...</Typography>
                ) : sourceOfTruthQuery.isError ? (
                  <Alert severity="error">
                    {sourceOfTruthQuery.error instanceof Error
                      ? sourceOfTruthQuery.error.message
                      : 'Failed to load managed vector resource state.'}
                  </Alert>
                ) : managedVectorState ? (
                  <>
                    <Alert severity={alertSeverityForStatus(managedVectorState.driftDetected ? managedVectorState.driftStatus : managedVectorState.status, true)}>
                      {managedVectorState.driftDetected && managedVectorState.driftMessage
                        ? managedVectorState.driftMessage
                        : managedVectorState.summaryMessage}
                    </Alert>

                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Strategy: ${managedVectorState.vectorStrategy}`} variant="outlined" />
                      <Chip label={`Provisioning: ${managedVectorState.vectorProvisioningMode}`} variant="outlined" />
                      <Chip label={`Active: ${managedVectorState.activeResourceCount}`} variant="outlined" />
                      <Chip label={`Detached: ${managedVectorState.detachedResourceCount}`} variant="outlined" />
                      <Chip label={`Status: ${managedVectorState.status}`} color={serviceStatusColor(managedVectorState.status)} />
                      {managedVectorState.driftDetected ? (
                        <>
                          <Chip label={`Drifted: ${managedVectorState.driftedResourceCount}`} color="warning" variant="outlined" />
                          <Chip label={`Drift status: ${managedVectorState.driftStatus}`} color={serviceStatusColor(managedVectorState.driftStatus)} />
                        </>
                      ) : null}
                    </Stack>

                    {managedVectorState.resources.length > 0 ? (
                      <Grid container spacing={2}>
                        {managedVectorState.resources.map((resource) => (
                          <Grid item xs={12} md={6} xl={4} key={resource.id}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                              <CardContent>
                                <Stack spacing={1.25}>
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                      {resource.vendor} {resource.resourceType.toLowerCase()}
                                    </Typography>
                                    <Chip
                                      label={resource.resourceStatus}
                                      size="small"
                                      color={resource.resourceStatus === 'ACTIVE' ? 'success' : 'default'}
                                    />
                                    {resource.provisioningState ? <Chip label={resource.provisioningState} size="small" variant="outlined" /> : null}
                                    {resource.driftState !== 'ALIGNED' ? (
                                      <Chip label={resource.driftState} size="small" color={resource.driftState === 'DETACHED_HISTORY' ? 'default' : 'warning'} variant="outlined" />
                                    ) : null}
                                    {resource.deletionStatus ? (
                                      <Chip
                                        label={`Delete ${resource.deletionStatus}`}
                                        size="small"
                                        color={deletionChipColor(resource.deletionStatus)}
                                        variant="outlined"
                                      />
                                    ) : null}
                                  </Stack>
                                  <Typography variant="body2" color="text.secondary">
                                    {resource.resourceName}
                                  </Typography>
                                  <Typography variant="body2">
                                    Version: <strong>{resource.deploymentVersionId ?? 'Unavailable'}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Endpoint: <strong>{resource.endpoint ?? 'Not captured'}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Secret refs: <strong>{resource.secretReferenceNames.length > 0 ? resource.secretReferenceNames.join(', ') : 'None'}</strong>
                                  </Typography>
                                  {resource.driftMessage ? (
                                    <Alert severity={resource.driftState === 'DETACHED_HISTORY' ? 'info' : 'warning'}>
                                      {resource.driftMessage}
                                    </Alert>
                                  ) : null}
                                  {resource.deletionStatus ? (
                                    <Alert severity={resource.deletionStatus === 'FAILED' ? 'error' : 'info'}>
                                      {resource.deletionStatus === 'FAILED'
                                        ? 'Cleanup failed for this managed vector resource. Review the admin notifications page for full delete details.'
                                        : `This managed vector resource is subject to deletion completion (${resource.deletionStatus.toLowerCase()}).`}
                                    </Alert>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ))}
                      </Grid>
                    ) : (
                      <Alert severity="info">No managed vector resource records exist for this deployment yet.</Alert>
                    )}
                  </>
                ) : (
                  <Alert severity="info">No managed vector resource state is available for this deployment yet.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Governed remediation actions</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Safe operator and admin recovery actions with role checks, confirmation gates, optional approval ids,
                    and audit-backed execution.
                  </Typography>
                </Box>

                {remediationQuery.isLoading ? (
                  <Typography color="text.secondary">Loading remediation actions...</Typography>
                ) : remediationQuery.isError ? (
                  <Alert severity="error">
                    {remediationQuery.error instanceof Error
                      ? remediationQuery.error.message
                      : 'Failed to load governed remediation actions.'}
                  </Alert>
                ) : remediationQuery.data ? (
                  <>
                    <Alert severity={remediationQuery.data.providerDriftDetected || remediationQuery.data.managedVectorDriftDetected ? 'warning' : 'info'}>
                      {remediationQuery.data.providerDriftDetected && remediationQuery.data.providerDriftMessage
                        ? remediationQuery.data.providerDriftMessage
                        : remediationQuery.data.managedVectorDriftDetected && remediationQuery.data.managedVectorDriftMessage
                          ? remediationQuery.data.managedVectorDriftMessage
                        : remediationQuery.data.summaryMessage}
                    </Alert>
                    <Grid container spacing={2}>
                      {remediationQuery.data.actions.map((action) => (
                        <Grid item xs={12} md={6} xl={4} key={action.key}>
                          <Card variant="outlined" sx={{ height: '100%' }}>
                            <CardContent>
                              <Stack spacing={1.25}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                    {action.label}
                                  </Typography>
                                  <Chip label={action.category} size="small" variant="outlined" />
                                  <Chip label={action.severity} size="small" color={remediationSeverityColor(action.severity)} variant="outlined" />
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                  {action.description}
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip label={`Role: ${action.requiredRole}`} size="small" variant="outlined" />
                                  {action.requiresApproval ? <Chip label="Approval id required" size="small" color="warning" variant="outlined" /> : null}
                                  {action.requiresConfirmation ? <Chip label="Confirmation required" size="small" variant="outlined" /> : null}
                                </Stack>
                                {action.available ? (
                                  <Alert severity="info">{action.confirmationText}</Alert>
                                ) : (
                                  <Alert severity="warning">{action.blockedReason ?? 'This action is not available.'}</Alert>
                                )}
                                {action.operatorGuidance ? (
                                  <Alert severity={action.available ? 'info' : 'warning'}>
                                    {action.operatorGuidance}
                                  </Alert>
                                ) : null}
                                <Button
                                  variant={action.available ? 'contained' : 'outlined'}
                                  color={action.severity === 'ERROR' ? 'warning' : 'primary'}
                                  disabled={!action.available}
                                  onClick={() => {
                                    setSelectedRemediationAction(action)
                                    setRemediationConfirmed(false)
                                    setRemediationReason('')
                                    setRemediationApprovalId('')
                                  }}
                                >
                                  Run {action.label}
                                </Button>
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

          <Card id="railway-logs" sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Release timeline</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    One place for the latest release step progression, failed step visibility, and release history signal.
                  </Typography>
                </Box>

                {latestRelease ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Release ${latestRelease.id}`} variant="outlined" />
                      <Chip label={`Version ${latestRelease.deploymentVersionId}`} variant="outlined" />
                      <Chip label={latestRelease.status} color={releaseStatusColor(latestRelease.status)} />
                      <Chip label={provisioningSummary.progress.currentStepStatus} color={releaseSignalColor(provisioningSummary.progress.currentStepStatus)} variant="outlined" />
                    </Stack>

                    {provisioningSummary.progress.steps.length === 0 ? (
                      <Alert severity="info">The latest release does not contain detailed step history yet.</Alert>
                    ) : (
                      <Grid container spacing={2}>
                        {provisioningSummary.progress.steps.map((step) => (
                          <Grid item xs={12} md={6} xl={4} key={`${latestRelease.id}:${step.key}`}>
                            <Card variant="outlined" sx={{ height: '100%' }}>
                              <CardContent>
                                <Stack spacing={1}>
                                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                                      {step.description}
                                    </Typography>
                                    <Chip label={step.status} size="small" color={releaseSignalColor(step.status)} variant="outlined" />
                                  </Stack>
                                  <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                    {step.key}
                                  </Typography>
                                  <Typography variant="body2">
                                    Started: <strong>{formatOptionalTimestamp(step.startedAt)}</strong>
                                  </Typography>
                                  <Typography variant="body2">
                                    Completed: <strong>{formatOptionalTimestamp(step.completedAt)}</strong>
                                  </Typography>
                                  {step.errorMessage ? (
                                    <Alert severity="error">{step.errorMessage}</Alert>
                                  ) : null}
                                </Stack>
                              </CardContent>
                            </Card>
                          </Grid>
                        ))}
                      </Grid>
                    )}
                  </>
                ) : (
                  <Alert severity="info">Apply a version first to inspect release timeline evidence.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2.5}>
            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Current endpoints</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Runtime service URL and internal connector URL currently attached to the deployment record.
                    </Typography>
                    <Typography variant="body2">
                      Runtime: <strong>{selectedDeployment.runtimeBaseUrl ?? 'Not assigned'}</strong>
                    </Typography>
                    <Typography variant="body2">
                      Runtime Swagger:{' '}
                      {swaggerUiUrl(selectedDeployment.runtimeBaseUrl) ? (
                        <Link
                          href={swaggerUiUrl(selectedDeployment.runtimeBaseUrl) ?? undefined}
                          target="_blank"
                          rel="noreferrer"
                          underline="hover"
                        >
                          Open docs
                        </Link>
                      ) : (
                        <strong>Not assigned</strong>
                      )}
                    </Typography>
                    <Typography variant="body2">
                      Connector (internal): <strong>{selectedDeployment.connectorBaseUrl ?? 'Not assigned'}</strong>
                    </Typography>
                    <Typography variant="body2">
                      Connector Swagger (internal):{' '}
                      {swaggerUiUrl(selectedDeployment.connectorBaseUrl) ? (
                        <Link
                          href={swaggerUiUrl(selectedDeployment.connectorBaseUrl) ?? undefined}
                          target="_blank"
                          rel="noreferrer"
                          underline="hover"
                        >
                          Open docs
                        </Link>
                      ) : (
                        <strong>Not assigned</strong>
                      )}
                    </Typography>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest provisioning evidence</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Latest stored provisioning result for the deployment’s most recent release.
                    </Typography>
                    {latestRelease ? (
                      <>
                        <Stack direction="row" spacing={1} flexWrap="wrap">
                          <Chip
                            label={latestRelease.status}
                            color={releaseStatusColor(latestRelease.status)}
                            sx={{ alignSelf: 'flex-start' }}
                          />
                          <Chip
                            label={`${latestRelease.provisioningStatus} · ${provisioningSummary.provider}`}
                            color={releaseSignalColor(latestRelease.provisioningStatus)}
                            variant="outlined"
                          />
                          <Chip
                            label={provisioningSummary.progress.currentStepStatus}
                            color={releaseSignalColor(provisioningSummary.progress.currentStepStatus)}
                            variant="outlined"
                          />
                        </Stack>
                        <Typography variant="body2">
                          Current step: {provisioningSummary.progress.currentStepDescription}
                        </Typography>
                        <Typography variant="body2">
                          Updated: {formatOptionalTimestamp(latestRelease.updatedAt)}
                        </Typography>
                        <Typography variant="body2">
                          Project:{' '}
                          {provisioningSummary.projectUrl ? (
                            <Link
                              href={provisioningSummary.projectUrl}
                              target="_blank"
                              rel="noreferrer"
                              underline="hover"
                            >
                              {provisioningSummary.projectName}
                            </Link>
                          ) : (
                            provisioningSummary.projectName
                          )}
                        </Typography>
                        <Typography variant="body2">Repository: {provisioningSummary.repository}</Typography>
                        <Typography variant="body2">Branch: {provisioningSummary.branch}</Typography>
                        <Typography variant="body2">Artifact strategy: {provisioningSummary.artifactStrategy}</Typography>
                        <Typography variant="body2">Runtime root: {provisioningSummary.runtimeRootDir}</Typography>
                        <Typography variant="body2">
                          Runtime Dockerfile: {provisioningSummary.runtimeDockerfilePath}
                        </Typography>
                        <Typography variant="body2">Connector root: {provisioningSummary.connectorRootDir}</Typography>
                        <Typography variant="body2">
                          Connector Dockerfile: {provisioningSummary.connectorDockerfilePath}
                        </Typography>
                        <Typography variant="body2">
                          Generated: {provisioningSummary.generatedAt === 'Unknown'
                            ? 'Unknown'
                            : formatTimestamp(provisioningSummary.generatedAt)}
                        </Typography>
                        {latestRelease.errorMessage || provisioningSummary.progress.errorMessage ? (
                          <Alert severity="error">
                            {latestRelease.errorMessage ?? provisioningSummary.progress.errorMessage}
                          </Alert>
                        ) : null}
                        <Stack spacing={0.5}>
                          <Typography variant="caption" color="text.secondary">
                            Artifact URLs
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            actions: {provisioningSummary.artifactUrls.actions}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            entities: {provisioningSummary.artifactUrls.entities}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            routing: {provisioningSummary.artifactUrls.routing}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            prompts: {provisioningSummary.artifactUrls.prompts}
                          </Typography>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                            manifest: {provisioningSummary.artifactUrls.manifest}
                          </Typography>
                        </Stack>
                      </>
                    ) : (
                      <Alert severity="info">No release evidence exists yet for this deployment.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Latest verification result</Typography>
                    <Typography variant="body2" color="text.secondary">
                      The latest stored verification run for the selected deployment.
                    </Typography>
                    {selectedRun ? (
                      <>
                        <Chip
                          icon={selectedRun.status === 'PASSED'
                            ? <CheckCircleRoundedIcon />
                            : <WarningAmberRoundedIcon />}
                          color={selectedRun.status === 'PASSED' ? 'success' : 'warning'}
                          label={selectedRun.status}
                          sx={{ alignSelf: 'flex-start' }}
                        />
                        <Typography variant="body2">{selectedRun.summaryMessage}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          Completed {formatTimestamp(selectedRun.completedAt)}
                        </Typography>
                      </>
                    ) : (
                      <Alert severity="info">No verification runs exist yet for this deployment.</Alert>
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Stack
                  direction={{ xs: 'column', md: 'row' }}
                  spacing={2}
                  alignItems={{ xs: 'stretch', md: 'center' }}
                >
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="h6">Railway logs</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Release-aware deployment logs pulled through the platform backend using Railway GraphQL.
                    </Typography>
                  </Box>
                  <TextField
                    select
                    label="Service"
                    value={selectedLogService}
                    onChange={(event) => setSelectedLogService(event.target.value as 'runtime' | 'restConnector' | 'vectorizationRunner')}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="runtime">Runtime</MenuItem>
                    <MenuItem value="restConnector">REST connector</MenuItem>
                    <MenuItem value="vectorizationRunner">Vectorization runner</MenuItem>
                  </TextField>
                  <TextField
                    select
                    label="Source"
                    value={selectedLogSource}
                    onChange={(event) => setSelectedLogSource(event.target.value)}
                    sx={{ minWidth: 180 }}
                  >
                    <MenuItem value="deployment">Deployment</MenuItem>
                    <MenuItem value="build">Build</MenuItem>
                    <MenuItem value="http">HTTP</MenuItem>
                  </TextField>
                </Stack>

                {railwayLogsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading Railway logs...</Typography>
                ) : railwayLogsQuery.isError ? (
                  <Alert severity="error">
                    {railwayLogsQuery.error instanceof Error
                      ? railwayLogsQuery.error.message
                      : 'Failed to load Railway logs'}
                  </Alert>
                ) : railwayLogs ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        label={railwayLogs.available ? 'Railway logs available' : 'Railway logs unavailable'}
                        color={railwayLogs.available ? 'success' : 'warning'}
                      />
                      <Chip label={`Service: ${railwayLogs.service}`} variant="outlined" />
                      <Chip label={`Source: ${railwayLogs.source}`} variant="outlined" />
                      <Chip label={`Limit: ${railwayLogs.requestedLimit}`} variant="outlined" />
                      {railwayLogs.releaseId ? (
                        <Chip label={`Release: ${railwayLogs.releaseId}`} variant="outlined" />
                      ) : null}
                    </Stack>

                    <Typography variant="body2" color="text.secondary">
                      {railwayLogs.message}
                    </Typography>

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        label={`Queried: ${formatTimestamp(railwayLogs.queriedAt)}`}
                        variant="outlined"
                      />
                      {railwayLogs.serviceName ? (
                        <Chip label={`Railway service: ${railwayLogs.serviceName}`} variant="outlined" />
                      ) : null}
                      {railwayLogs.railwayDeploymentId ? (
                        <Chip label={`Deployment: ${railwayLogs.railwayDeploymentId}`} variant="outlined" />
                      ) : null}
                    </Stack>

                    {!railwayLogs.available ? (
                      <Alert severity="info">{railwayLogs.message}</Alert>
                    ) : railwayLogs.entries.length === 0 ? (
                      <Alert severity="info">No Railway log entries matched the current query.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Timestamp</TableCell>
                            <TableCell>Severity</TableCell>
                            <TableCell>Message</TableCell>
                            <TableCell>Attributes</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {railwayLogs.entries.map((entry, index) => (
                            <TableRow key={`${entry.timestamp ?? 'unknown'}:${index}`} hover>
                              <TableCell sx={{ whiteSpace: 'nowrap' }}>
                                {entry.timestamp ? formatTimestamp(entry.timestamp) : '—'}
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={entry.severity ?? 'UNKNOWN'}
                                  color={entry.severity === 'ERROR' ? 'error' : entry.severity === 'WARN' ? 'warning' : 'default'}
                                />
                              </TableCell>
                              <TableCell sx={{ fontFamily: 'monospace', maxWidth: 520 }}>
                                {entry.message ?? '—'}
                              </TableCell>
                              <TableCell sx={{ fontFamily: 'monospace', maxWidth: 360 }}>
                                {summarizeLogAttributes(entry.attributes)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </>
                ) : (
                  <Alert severity="info">Apply a Railway-backed release first to inspect logs.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Release evidence</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Applied releases with provisioning and verification linkage.
                  </Typography>
                </Box>
                {releasesQuery.isLoading ? (
                  <Typography color="text.secondary">Loading releases...</Typography>
                ) : releasesQuery.isError ? (
                  <Alert severity="error">
                    {releasesQuery.error instanceof Error
                      ? releasesQuery.error.message
                      : 'Failed to load releases'}
                  </Alert>
                ) : releaseHistory.length === 0 ? (
                  <Alert severity="info">No release evidence exists yet for this deployment.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Release</TableCell>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Current step</TableCell>
                        <TableCell>Verification</TableCell>
                        <TableCell>Updated</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {releaseHistory.map((release) => (
                        <TableRow key={release.id} hover>
                          <TableCell>{release.id}</TableCell>
                          <TableCell>{release.deploymentVersionId}</TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={1} flexWrap="wrap">
                              <Chip
                                size="small"
                                label={release.status}
                                color={releaseStatusColor(release.status)}
                              />
                              <Chip
                                size="small"
                                label={release.provisioningStatus}
                                color={releaseSignalColor(release.provisioningStatus)}
                                variant="outlined"
                              />
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">
                                {release.currentStepDescription ?? 'Not recorded yet'}
                              </Typography>
                              {release.errorMessage ? (
                                <Typography variant="caption" color="error.main">
                                  {release.errorMessage}
                                </Typography>
                              ) : null}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Chip
                                size="small"
                                label={release.verificationStatus}
                                color={releaseSignalColor(release.verificationStatus)}
                                variant="outlined"
                              />
                              <Typography variant="caption" color="text.secondary">
                                {release.verificationRunId ?? 'No verification run'}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{formatOptionalTimestamp(release.updatedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Provisioning step history</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Per-step progress evidence stored on the latest release while Railway apply runs.
                  </Typography>
                </Box>
                {latestRelease ? (
                  provisioningSummary.progress.steps.length === 0 ? (
                    <Alert severity="info">The latest release does not contain detailed step history yet.</Alert>
                  ) : (
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Step</TableCell>
                          <TableCell>Status</TableCell>
                          <TableCell>Started</TableCell>
                          <TableCell>Completed</TableCell>
                          <TableCell>Error</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {provisioningSummary.progress.steps.map((step) => (
                          <TableRow key={`${latestRelease.id}:${step.key}`} hover>
                            <TableCell>
                              <Stack spacing={0.25}>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {step.description}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                  {step.key}
                                </Typography>
                              </Stack>
                            </TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={step.status}
                                color={releaseSignalColor(step.status)}
                              />
                            </TableCell>
                            <TableCell>{formatOptionalTimestamp(step.startedAt)}</TableCell>
                            <TableCell>{formatOptionalTimestamp(step.completedAt)}</TableCell>
                            <TableCell sx={{ maxWidth: 320 }}>
                              {step.errorMessage ?? '—'}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )
                ) : (
                  <Alert severity="info">Apply a version first to inspect provisioning step history.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Verification runs</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Stored verification evidence created during apply and manual reruns.
                  </Typography>
                </Box>
                {verificationRunsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading verification runs...</Typography>
                ) : verificationRunsQuery.isError ? (
                  <Alert severity="error">
                    {verificationRunsQuery.error instanceof Error
                      ? verificationRunsQuery.error.message
                      : 'Failed to load verification runs'}
                  </Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Run</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Summary</TableCell>
                        <TableCell>Completed</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {verificationRuns.map((run) => (
                        <TableRow
                          key={run.id}
                          hover
                          selected={run.id === selectedRun?.id}
                          onClick={() => setSelectedRunId(run.id)}
                          sx={{ cursor: 'pointer' }}
                        >
                          <TableCell>{run.id}</TableCell>
                          <TableCell>{run.verificationType}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={run.status === 'PASSED' ? 'success' : 'warning'}
                              icon={run.status === 'PASSED' ? <CheckCircleRoundedIcon /> : <WarningAmberRoundedIcon />}
                              label={run.status}
                            />
                          </TableCell>
                          <TableCell>{run.summaryMessage}</TableCell>
                          <TableCell>{formatTimestamp(run.completedAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Selected run checks</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Detailed check evidence for the currently selected verification run.
                  </Typography>
                </Box>

                {selectedRun ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={selectedRun.id} variant="outlined" />
                      <Chip label={selectedRun.verificationType} variant="outlined" />
                      <Chip label={selectedRun.status} color={selectedRun.status === 'PASSED' ? 'success' : 'warning'} />
                    </Stack>

                    {selectedRunChecks.length === 0 ? (
                      <Alert severity="info">This verification run does not contain detailed checks.</Alert>
                    ) : (
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Check</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Message</TableCell>
                            <TableCell>Details</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {selectedRunChecks.map((check) => (
                            <TableRow key={`${selectedRun.id}:${check.name}`} hover>
                              <TableCell sx={{ fontFamily: 'monospace' }}>{check.name}</TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  color={statusColor(check.status)}
                                  label={check.status}
                                />
                              </TableCell>
                              <TableCell>{check.message}</TableCell>
                              <TableCell sx={{ fontFamily: 'monospace', maxWidth: 360 }}>
                                {formatDetails(check.details)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </>
                ) : (
                  <Alert severity="info">Select or run verification to inspect detailed checks.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Audit events</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Recent privileged platform actions recorded with actor, role, target, and action details.
                  </Typography>
                </Box>
                {auditEventsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading audit events...</Typography>
                ) : auditEventsQuery.isError ? (
                  <Alert severity="error">
                    {auditEventsQuery.error instanceof Error
                      ? auditEventsQuery.error.message
                      : 'Failed to load audit events'}
                  </Alert>
                ) : auditEvents.length === 0 ? (
                  <Alert severity="info">No audit events recorded yet for this deployment.</Alert>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Time</TableCell>
                        <TableCell>Actor</TableCell>
                        <TableCell>Role</TableCell>
                        <TableCell>Action</TableCell>
                        <TableCell>Target</TableCell>
                        <TableCell>Details</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {auditEvents.map((event) => (
                        <TableRow key={event.id} hover>
                          <TableCell>{formatTimestamp(event.createdAt)}</TableCell>
                          <TableCell>{event.actorId}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={event.actorRole}
                              color={event.actorRole === 'PLATFORM_ADMIN' ? 'secondary' : 'primary'}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{event.action}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2">{event.targetType}</Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                {event.targetId}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 360, fontFamily: 'monospace' }}>
                            {summarizeAuditDetails(event.details)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Stack>
            </CardContent>
          </Card>
        </>
      ) : null}

      <Dialog
        open={selectedRemediationAction != null}
        onClose={() => {
          if (remediationMutation.isPending) {
            return
          }
          setSelectedRemediationAction(null)
          setRemediationConfirmed(false)
          setRemediationReason('')
          setRemediationApprovalId('')
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{selectedRemediationAction?.label}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity={selectedRemediationAction?.severity === 'ERROR' ? 'warning' : 'info'}>
              {selectedRemediationAction?.confirmationText}
            </Alert>
            {selectedRemediationAction?.requiresReason ? (
              <TextField
                label="Reason"
                value={remediationReason}
                onChange={(event) => setRemediationReason(event.target.value)}
                multiline
                minRows={3}
                fullWidth
              />
            ) : null}
            {selectedRemediationAction?.requiresApproval ? (
              <TextField
                label="Approved action id"
                value={remediationApprovalId}
                onChange={(event) => setRemediationApprovalId(event.target.value)}
                helperText="Provide the approved deployment operation id if the deployment guardrail requires one."
                fullWidth
              />
            ) : null}
            {selectedRemediationAction?.requiresConfirmation ? (
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={remediationConfirmed}
                    onChange={(event) => setRemediationConfirmed(event.target.checked)}
                  />
                )}
                label="I understand this action affects live deployment state."
              />
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setSelectedRemediationAction(null)
              setRemediationConfirmed(false)
              setRemediationReason('')
              setRemediationApprovalId('')
            }}
            disabled={remediationMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={
              remediationMutation.isPending
              || !selectedRemediationAction
              || (selectedRemediationAction.requiresConfirmation && !remediationConfirmed)
              || (selectedRemediationAction.requiresReason && remediationReason.trim().length === 0)
            }
            onClick={() => {
              if (!selectedRemediationAction) {
                return
              }
              remediationMutation.mutate({
                actionKey: selectedRemediationAction.key,
                confirm: selectedRemediationAction.requiresConfirmation ? remediationConfirmed : true,
                reason: remediationReason.trim() || undefined,
                approvalId: remediationApprovalId.trim() || undefined,
              })
            }}
          >
            {remediationMutation.isPending ? 'Running...' : selectedRemediationAction ? `Run ${selectedRemediationAction.label}` : 'Run action'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
