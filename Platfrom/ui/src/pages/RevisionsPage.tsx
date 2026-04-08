import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import PublishRoundedIcon from '@mui/icons-material/PublishRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
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
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  applyDeploymentVersion,
  fetchDeploymentConfigDiffCenter,
  fetchDeploymentDraft,
  fetchDeploymentIntegrationSummary,
  fetchPlatformUserPreferences,
  fetchDeploymentReleases,
  fetchDeploymentVersions,
  fetchRailwayProvisioningPlan,
  publishDeploymentDraft,
  reconcileDeploymentRelease,
  updatePlatformUserPreferences,
  updateDeploymentSource,
  type DeploymentConfigDiffCenterSummary,
  type DeploymentDraftResponse,
  type DeploymentReleaseSummary,
  type DeploymentRevisionsViewPreferences,
  type RailwayEnvVarSummary,
  type RailwayProvisioningPlanSummary,
  type RailwayServicePlanSummary,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import {
  integrationModeColor,
  integrationModeLabel,
} from '../workspace/deploymentIntegrationSummary'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

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

function readString(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Not configured'
  }

  const value = config[key]
  return typeof value === 'string' && value.length > 0 ? value : 'Not configured'
}

function readBoolean(config: unknown, key: string): string {
  if (!isRecord(config)) {
    return 'Unknown'
  }

  const value = config[key]
  return typeof value === 'boolean' ? String(value) : 'Unknown'
}

function formatTimestamp(value: string): string {
  return new Date(value).toLocaleString()
}

function formatOptionalTimestamp(value: string | null | undefined): string {
  return value ? formatTimestamp(value) : '—'
}

function normalizedText(value: string | null | undefined): string {
  return (value ?? '').trim().toLowerCase()
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

function readRailwayProjectUrl(provisioningDetails: unknown): string | null {
  if (!isRecord(provisioningDetails)) {
    return null
  }

  if (typeof provisioningDetails.projectId === 'string' && provisioningDetails.projectId.length > 0) {
    return `https://railway.com/project/${provisioningDetails.projectId}`
  }

  const railway = isRecord(provisioningDetails.railway) ? provisioningDetails.railway : null
  if (railway && typeof railway.projectId === 'string' && railway.projectId.length > 0) {
    return `https://railway.com/project/${railway.projectId}`
  }

  return null
}

function readRailwayProjectName(provisioningDetails: unknown): string | null {
  if (!isRecord(provisioningDetails)) {
    return null
  }

  if (typeof provisioningDetails.projectName === 'string' && provisioningDetails.projectName.length > 0) {
    return provisioningDetails.projectName
  }

  const railway = isRecord(provisioningDetails.railway) ? provisioningDetails.railway : null
  if (railway && typeof railway.projectName === 'string' && railway.projectName.length > 0) {
    return railway.projectName
  }

  return null
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

function verificationStatusColor(status: string): 'success' | 'warning' | 'info' | 'default' {
  if (status === 'PASSED') {
    return 'success'
  }
  if (status === 'RUNNING') {
    return 'info'
  }
  if (status === 'FAILED') {
    return 'warning'
  }
  return 'default'
}

function provisioningStatusColor(status: string): 'success' | 'warning' | 'info' | 'default' {
  if (status === 'SUCCEEDED' || status === 'PLANNED') {
    return 'success'
  }
  if (status === 'AWAITING_CONFIRMATION') {
    return 'warning'
  }
  if (status === 'QUEUED' || status === 'RUNNING') {
    return 'info'
  }
  if (status === 'FAILED') {
    return 'warning'
  }
  return 'default'
}

function isActivationUnconfirmed(release: DeploymentReleaseSummary | null | undefined): boolean {
  return release?.status === 'PROVISIONING' && release.provisioningStatus === 'AWAITING_CONFIRMATION'
}

function isReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
}

function revisionsViewEquals(
  saved: DeploymentRevisionsViewPreferences | null | undefined,
  current: DeploymentRevisionsViewPreferences,
): boolean {
  if (!saved) {
    return false
  }
  return saved.searchTerm === current.searchTerm
    && saved.versionStatusFilter === current.versionStatusFilter
    && saved.releaseStatusFilter === current.releaseStatusFilter
    && saved.reindexFilter === current.reindexFilter
}

function summarizeDraft(draft: DeploymentDraftResponse | undefined) {
  if (!draft) {
    return {
      actions: 0,
      entitySpaces: 0,
      routingActions: 0,
      llmProvider: 'Not configured',
      vectorStrategy: 'Not configured',
      authzMode: 'Not configured',
      adminApiKeyEnabled: 'Unknown',
    }
  }

  return {
    actions: countActions(draft.actionsConfig),
    entitySpaces: countEntitySpaces(draft.entityConfig),
    routingActions: countRoutingActions(draft.routingConfig),
    llmProvider: readString(draft.providerConfig, 'llmProvider'),
    vectorStrategy: readString(draft.providerConfig, 'vectorStrategy'),
    authzMode: readString(draft.securityConfig, 'authzMode'),
    adminApiKeyEnabled: readBoolean(draft.securityConfig, 'adminApiKeyEnabled'),
  }
}

type PlanEnvImpactEntry = {
  key: string
  change: 'ADDED' | 'CHANGED' | 'REMOVED' | 'UNCHANGED'
  currentValue: string | null
  nextValue: string | null
  usesSecretReference: boolean
}

type PlanEnvImpactSummary = {
  added: number
  changed: number
  removed: number
  unchanged: number
  changedEntries: PlanEnvImpactEntry[]
}

type ServiceImpactSummary = {
  changed: boolean
  rootDirChanged: boolean
  dockerfileChanged: boolean
  baseUrlChanged: boolean
  env: PlanEnvImpactSummary
}

function isSecretReference(value: string | null | undefined): boolean {
  return typeof value === 'string' && value.startsWith('${secret:') && value.endsWith('}')
}

function envMap(entries: RailwayEnvVarSummary[] | undefined): Map<string, string> {
  return new Map((entries ?? []).map((entry) => [entry.key, entry.value]))
}

function diffEnvImpact(currentEnv: RailwayEnvVarSummary[] | undefined, nextEnv: RailwayEnvVarSummary[]): PlanEnvImpactSummary {
  const current = envMap(currentEnv)
  const next = envMap(nextEnv)
  const keys = Array.from(new Set([...current.keys(), ...next.keys()])).sort((left, right) => left.localeCompare(right))
  const changedEntries: PlanEnvImpactEntry[] = []
  let added = 0
  let changed = 0
  let removed = 0
  let unchanged = 0

  keys.forEach((key) => {
    const currentValue = current.get(key) ?? null
    const nextValue = next.get(key) ?? null
    let change: PlanEnvImpactEntry['change']
    if (currentValue == null && nextValue != null) {
      change = 'ADDED'
      added += 1
    } else if (currentValue != null && nextValue == null) {
      change = 'REMOVED'
      removed += 1
    } else if (currentValue !== nextValue) {
      change = 'CHANGED'
      changed += 1
    } else {
      change = 'UNCHANGED'
      unchanged += 1
    }

    if (change !== 'UNCHANGED') {
      changedEntries.push({
        key,
        change,
        currentValue,
        nextValue,
        usesSecretReference: isSecretReference(currentValue) || isSecretReference(nextValue),
      })
    }
  })

  return {
    added,
    changed,
    removed,
    unchanged,
    changedEntries,
  }
}

function buildServiceImpact(
  currentService: RailwayServicePlanSummary | null | undefined,
  nextService: RailwayServicePlanSummary,
): ServiceImpactSummary {
  const env = diffEnvImpact(currentService?.env, nextService.env)
  const rootDirChanged = (currentService?.rootDir ?? null) !== (nextService.rootDir ?? null)
  const dockerfileChanged = (currentService?.dockerfilePath ?? null) !== (nextService.dockerfilePath ?? null)
  const baseUrlChanged = (currentService?.baseUrl ?? null) !== nextService.baseUrl
  return {
    changed: !currentService
      || rootDirChanged
      || dockerfileChanged
      || baseUrlChanged
      || env.changedEntries.length > 0,
    rootDirChanged,
    dockerfileChanged,
    baseUrlChanged,
    env,
  }
}

function artifactChanges(
  currentPlan: RailwayProvisioningPlanSummary | null | undefined,
  nextPlan: RailwayProvisioningPlanSummary,
): string[] {
  const fields: Array<keyof RailwayProvisioningPlanSummary['artifactUrls']> = ['actions', 'entities', 'routing', 'manifest']
  return fields.filter((field) => currentPlan?.artifactUrls?.[field] !== nextPlan.artifactUrls[field])
}

function impactSummaryMessage(
  currentPlan: RailwayProvisioningPlanSummary | null | undefined,
  nextPlan: RailwayProvisioningPlanSummary,
  runtimeImpact: ServiceImpactSummary,
  connectorImpact: ServiceImpactSummary,
  changedArtifacts: string[],
): string {
  if (!currentPlan) {
    return `First apply for ${nextPlan.versionLabel}: runtime, the internal REST connector service, immutable artifacts, and deployment access surfaces will be created.`
  }
  if (
    !runtimeImpact.changed
    && !connectorImpact.changed
    && changedArtifacts.length === 0
  ) {
    return `Selected version ${nextPlan.versionLabel} already matches the current live release plan.`
  }
  return `Applying ${nextPlan.versionLabel} will update ${changedArtifacts.length} artifact link(s), ${runtimeImpact.env.changedEntries.length} runtime env reference(s), and ${connectorImpact.env.changedEntries.length} internal REST connector env reference(s).`
}

function envChangeColor(change: PlanEnvImpactEntry['change']): 'success' | 'warning' | 'error' | 'default' {
  if (change === 'ADDED') {
    return 'success'
  }
  if (change === 'CHANGED') {
    return 'warning'
  }
  if (change === 'REMOVED') {
    return 'error'
  }
  return 'default'
}

function diffStateColor(state: string): 'success' | 'warning' | 'info' | 'default' {
  if (state === 'ALIGNED') {
    return 'success'
  }
  if (state === 'LIVE_OUTDATED' || state === 'PUBLISHED_NOT_APPLIED') {
    return 'info'
  }
  if (state === 'DRAFT_ONLY') {
    return 'default'
  }
  return 'warning'
}

function referenceChipColor(reference: DeploymentConfigDiffCenterSummary['draft']): 'primary' | 'success' | 'default' {
  if (!reference.available) {
    return 'default'
  }
  return reference.stage === 'DRAFT' ? 'primary' : 'success'
}

export function RevisionsPage() {
  const auth = usePlatformAuth()
  const navigate = useNavigate()
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [selectedVersionId, setSelectedVersionId] = useState('')
  const [sourceRepositoryInput, setSourceRepositoryInput] = useState('')
  const [sourceBranchInput, setSourceBranchInput] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [versionStatusFilter, setVersionStatusFilter] = useState('ALL')
  const [releaseStatusFilter, setReleaseStatusFilter] = useState('ALL')
  const [reindexFilter, setReindexFilter] = useState('ALL')
  const viewInitializedRef = useRef(false)
  const viewHydrationRef = useRef(false)

  const selectedDeployment = useMemo(
    () => workspace?.deployment ?? selectedDeploymentSummary ?? null,
    [selectedDeploymentSummary, workspace],
  )

  useEffect(() => {
    setSourceRepositoryInput(selectedDeployment?.source.repositoryOverride ?? '')
    setSourceBranchInput(selectedDeployment?.source.branchOverride ?? '')
  }, [selectedDeployment?.id, selectedDeployment?.source.branchOverride, selectedDeployment?.source.repositoryOverride])

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const diffCenterQuery = useQuery({
    queryKey: ['deployment-config-diff-center', selectedDeploymentId],
    queryFn: () => fetchDeploymentConfigDiffCenter(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const versionsQuery = useQuery({
    queryKey: ['deployment-versions', selectedDeploymentId],
    queryFn: () => fetchDeploymentVersions(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const preferencesQuery = useQuery({
    queryKey: ['platform-preferences'],
    queryFn: fetchPlatformUserPreferences,
  })
  const updatePreferencesMutation = useMutation({
    mutationFn: updatePlatformUserPreferences,
    onSuccess: (data) => {
      queryClient.setQueryData(['platform-preferences'], data)
    },
  })

  const versions = versionsQuery.data ?? []

  useEffect(() => {
    if (viewInitializedRef.current || !preferencesQuery.isSuccess) {
      return
    }
    const savedView = preferencesQuery.data?.deploymentRevisionsView
    if (savedView) {
      setSearchTerm(savedView.searchTerm ?? '')
      setVersionStatusFilter(savedView.versionStatusFilter ?? 'ALL')
      setReleaseStatusFilter(savedView.releaseStatusFilter ?? 'ALL')
      setReindexFilter(savedView.reindexFilter ?? 'ALL')
    }
    viewHydrationRef.current = true
    viewInitializedRef.current = true
  }, [preferencesQuery.data, preferencesQuery.isSuccess])

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
  const latestReleaseByVersion = useMemo(() => {
    const byVersion = new Map<string, DeploymentReleaseSummary>()
    releaseHistory.forEach((release) => {
      if (!byVersion.has(release.deploymentVersionId)) {
        byVersion.set(release.deploymentVersionId, release)
      }
    })
    return byVersion
  }, [releaseHistory])

  const revisionsView = useMemo<DeploymentRevisionsViewPreferences>(() => ({
    searchTerm,
    versionStatusFilter,
    releaseStatusFilter,
    reindexFilter,
  }), [reindexFilter, releaseStatusFilter, searchTerm, versionStatusFilter])
  const viewMatchesSaved = useMemo(
    () => revisionsViewEquals(preferencesQuery.data?.deploymentRevisionsView, revisionsView),
    [preferencesQuery.data?.deploymentRevisionsView, revisionsView],
  )

  useEffect(() => {
    if (!preferencesQuery.isSuccess || !viewInitializedRef.current) {
      return
    }
    if (viewHydrationRef.current) {
      viewHydrationRef.current = false
      return
    }
    if (viewMatchesSaved) {
      return
    }
    const handle = window.setTimeout(() => {
      updatePreferencesMutation.mutate({ deploymentRevisionsView: revisionsView })
    }, 600)
    return () => window.clearTimeout(handle)
  }, [
    preferencesQuery.isSuccess,
    revisionsView,
    updatePreferencesMutation,
    viewMatchesSaved,
  ])

  const filteredVersions = useMemo(() => {
    const searchValue = normalizedText(searchTerm)
    return versions.filter((version) => {
      const latestReleaseForVersion = latestReleaseByVersion.get(version.id)
      const releaseStatus = latestReleaseForVersion?.status ?? 'UNAPPLIED'
      const matchesSearch = searchValue.length === 0
        || normalizedText(version.versionLabel).includes(searchValue)
        || normalizedText(version.id).includes(searchValue)
        || normalizedText(version.configHash).includes(searchValue)
      const matchesVersionStatus = versionStatusFilter === 'ALL' || version.status === versionStatusFilter
      const matchesReleaseStatus = releaseStatusFilter === 'ALL' || releaseStatus === releaseStatusFilter
      const matchesReindex = reindexFilter === 'ALL'
        || (reindexFilter === 'REINDEX_REQUIRED' && version.reindexRequired)
        || (reindexFilter === 'READY' && !version.reindexRequired)
      return matchesSearch && matchesVersionStatus && matchesReleaseStatus && matchesReindex
    })
  }, [latestReleaseByVersion, reindexFilter, releaseStatusFilter, searchTerm, versionStatusFilter, versions])

  const filteredReleaseHistory = useMemo(() => {
    const searchValue = normalizedText(searchTerm)
    return releaseHistory.filter((release) => {
      const matchesSearch = searchValue.length === 0
        || normalizedText(release.id).includes(searchValue)
        || normalizedText(release.deploymentVersionId).includes(searchValue)
        || normalizedText(release.currentStepDescription).includes(searchValue)
        || normalizedText(release.errorMessage).includes(searchValue)
      const matchesReleaseStatus = releaseStatusFilter === 'ALL' || release.status === releaseStatusFilter
      return matchesSearch && matchesReleaseStatus
    })
  }, [releaseHistory, releaseStatusFilter, searchTerm])

  useEffect(() => {
    if (filteredVersions.length === 0) {
      if (selectedVersionId !== '') {
        setSelectedVersionId('')
      }
      return
    }

    if (!filteredVersions.some((version) => version.id === selectedVersionId)) {
      setSelectedVersionId(filteredVersions[0].id)
    }
  }, [filteredVersions, selectedVersionId])

  const railwayPlanQuery = useQuery({
    queryKey: ['deployment-railway-plan', selectedDeploymentId, selectedVersionId],
    queryFn: () => fetchRailwayProvisioningPlan(selectedDeploymentId, selectedVersionId),
    enabled: selectedDeploymentId.length > 0 && selectedVersionId.length > 0,
  })
  const integrationSummaryQuery = useQuery({
    queryKey: ['deployment-integration-summary', selectedDeploymentId],
    queryFn: () => fetchDeploymentIntegrationSummary(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
    staleTime: 30_000,
  })
  const liveVersionId = workspace?.lifecycle.liveVersionId ?? ''
  const liveRailwayPlanQuery = useQuery({
    queryKey: ['deployment-railway-plan', selectedDeploymentId, liveVersionId],
    queryFn: () => fetchRailwayProvisioningPlan(selectedDeploymentId, liveVersionId),
    enabled: selectedDeploymentId.length > 0 && liveVersionId.length > 0 && liveVersionId !== selectedVersionId,
  })
  const latestRelease = releaseHistory[0] ?? null
  const inProgressRelease = releaseHistory.find(isReleaseInProgress) ?? null
  const integrationSummary = integrationSummaryQuery.data
  const latestRailwayProjectUrl = latestRelease ? readRailwayProjectUrl(latestRelease.provisioningDetails) : null
  const latestRailwayProjectName = latestRelease ? readRailwayProjectName(latestRelease.provisioningDetails) : null
  const runtimeSwaggerUrl = swaggerUiUrl(selectedDeployment?.runtimeBaseUrl)
  const connectorAdminUrl = selectedDeployment?.connectorBaseUrl
    ? joinUrl(selectedDeployment?.runtimeBaseUrl, '/api/admin/connector/overview')
    : null
  const activationUnconfirmed = isActivationUnconfirmed(latestRelease)

  useEffect(() => {
    if (!inProgressRelease) {
      return undefined
    }

    const intervalId = window.setInterval(() => {
      void queryClient.invalidateQueries({ queryKey: ['deployments'] })
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [inProgressRelease, queryClient])

  const publishMutation = useMutation({
    mutationFn: (draftId: string) => publishDeploymentDraft(draftId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-versions', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-railway-plan', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-config-diff-center', selectedDeploymentId] }),
      ])
    },
  })

  const applyMutation = useMutation({
    mutationFn: ({ deploymentId, versionId }: { deploymentId: string; versionId: string }) =>
      applyDeploymentVersion(deploymentId, versionId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-config-diff-center', selectedDeploymentId] }),
      ])
    },
  })

  const reconcileReleaseMutation = useMutation({
    mutationFn: (deploymentId: string) => reconcileDeploymentRelease(deploymentId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-releases', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-verification-runs', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
      ])
    },
  })

  const updateSourceMutation = useMutation({
    mutationFn: ({
      deploymentId,
      repository,
      branch,
    }: {
      deploymentId: string
      repository?: string
      branch?: string
    }) => updateDeploymentSource(deploymentId, { repository, branch }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-overviews'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-railway-plan', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-config-diff-center', selectedDeploymentId] }),
      ])
    },
  })

  const draftSummary = summarizeDraft(draftQuery.data)
  const isPlatformAdmin = auth.session?.role === 'PLATFORM_ADMIN'
  const canEdit = workspace?.access.canEdit ?? false
  const canOperate = workspace?.access.canOperate ?? false
  const canAdmin = workspace?.access.canAdmin ?? false

  const selectedVersion = useMemo(
    () => filteredVersions.find((version) => version.id === selectedVersionId) ?? null,
    [filteredVersions, selectedVersionId],
  )

  const plan = railwayPlanQuery.data
  const livePlan = selectedVersionId === liveVersionId ? plan : (liveRailwayPlanQuery.data ?? null)
  const runtimeImpact = useMemo(
    () => (plan ? buildServiceImpact(livePlan?.services.runtime, plan.services.runtime) : null),
    [livePlan, plan],
  )
  const connectorImpact = useMemo(
    () => (plan ? buildServiceImpact(livePlan?.services.restConnector, plan.services.restConnector) : null),
    [livePlan, plan],
  )
  const changedArtifacts = useMemo(
    () => (plan ? artifactChanges(livePlan, plan) : []),
    [livePlan, plan],
  )
  const impactMessage = useMemo(
    () => (plan && runtimeImpact && connectorImpact
      ? impactSummaryMessage(livePlan, plan, runtimeImpact, connectorImpact, changedArtifacts)
      : null),
    [changedArtifacts, connectorImpact, livePlan, plan, runtimeImpact],
  )

  const renderEnvTable = (entries: RailwayEnvVarSummary[]) => (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Key</TableCell>
          <TableCell>Value</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {entries.map((entry) => (
          <TableRow key={entry.key} hover>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.key}</TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  const renderEnvImpactTable = (entries: PlanEnvImpactEntry[]) => (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Key</TableCell>
          <TableCell>Change</TableCell>
          <TableCell>Current</TableCell>
          <TableCell>Next</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {entries.map((entry) => (
          <TableRow key={entry.key} hover>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.key}</TableCell>
            <TableCell>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <Chip size="small" label={entry.change} color={envChangeColor(entry.change)} variant="outlined" />
                {entry.usesSecretReference ? <Chip size="small" label="Secret ref" variant="outlined" /> : null}
              </Stack>
            </TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.currentValue ?? '—'}</TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{entry.nextValue ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Revisions" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Draft, publish, apply
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 960 }}>
          The release lifecycle now stores more than state transitions: publishing creates immutable
          versions, applying produces provisioning evidence, and each release can point to a stored
          verification run.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Versioning and release operations now stay anchored to the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            {selectedDeployment ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={selectedDeployment.name} variant="outlined" />
                <Chip label={selectedDeployment.environment} variant="outlined" />
                <Chip label={selectedDeployment.status} color="primary" />
                <Chip label={`Active: ${selectedDeployment.activeVersion ?? '—'}`} variant="outlined" />
                <Chip label={workspace?.template.name ?? selectedDeployment.templateId} variant="outlined" />
                <Chip label={`Source: ${selectedDeployment.source.branch}`} variant="outlined" />
                {inProgressRelease ? (
                  <Chip
                    label={`Release running: ${inProgressRelease.currentStepDescription ?? inProgressRelease.status}`}
                    color="info"
                  />
                ) : null}
              </Stack>
            ) : (
              <Alert severity="info">Create a deployment first to start managing revisions.</Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      {selectedDeployment ? (
        <>
          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Deployment source</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    This controls which GitHub repo and branch Railway will deploy for this
                    deployment. Keep this admin-only. Customers should choose templates, not raw
                    branches.
                  </Typography>
                </Box>

                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip label={`Effective repo: ${selectedDeployment.source.repository}`} variant="outlined" />
                  <Chip label={`Effective branch: ${selectedDeployment.source.branch}`} variant="outlined" />
                  {selectedDeployment.source.overrideActive ? (
                    <Chip label="Override active" color="secondary" />
                  ) : (
                    <Chip label="Using platform default" variant="outlined" />
                  )}
                </Stack>

                {canAdmin ? (
                  <>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <TextField
                          label="Repository override"
                          value={sourceRepositoryInput}
                          onChange={(event) => setSourceRepositoryInput(event.target.value)}
                          fullWidth
                          helperText="Optional. Use owner/repo. Leave blank to keep the platform default repository."
                        />
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <TextField
                          label="Branch override"
                          value={sourceBranchInput}
                          onChange={(event) => setSourceBranchInput(event.target.value)}
                          fullWidth
                          helperText="Optional. Leave blank to keep the platform default branch."
                        />
                      </Grid>
                    </Grid>

                    <Stack direction="row" spacing={1}>
                      <Button
                        variant="contained"
                        disabled={updateSourceMutation.isPending || !canAdmin}
                        onClick={() =>
                          updateSourceMutation.mutate({
                            deploymentId: selectedDeployment.id,
                            repository: sourceRepositoryInput,
                            branch: sourceBranchInput,
                          })}
                      >
                        {updateSourceMutation.isPending ? 'Saving source...' : 'Save source override'}
                      </Button>
                      <Button
                        variant="outlined"
                        color="inherit"
                        disabled={
                          updateSourceMutation.isPending
                          || !canAdmin
                          || (!selectedDeployment.source.repositoryOverride && !selectedDeployment.source.branchOverride)
                        }
                        onClick={() => {
                          setSourceRepositoryInput('')
                          setSourceBranchInput('')
                          updateSourceMutation.mutate({
                            deploymentId: selectedDeployment.id,
                            repository: '',
                            branch: '',
                          })
                        }}
                      >
                        Clear override
                      </Button>
                    </Stack>
                  </>
                ) : (
                  <Alert severity="info">
                    Changing deployment source is restricted to deployment admins.
                  </Alert>
                )}

                {updateSourceMutation.isError ? (
                  <Alert severity="error">
                    {updateSourceMutation.error instanceof Error
                      ? updateSourceMutation.error.message
                      : 'Failed to update deployment source'}
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Grid container spacing={2.5}>
            <Grid item xs={12} lg={4}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Typography variant="h6">Active draft</Typography>
                    {draftQuery.isLoading ? (
                      <Typography color="text.secondary">Loading draft...</Typography>
                    ) : draftQuery.isError ? (
                      <Alert severity="error">
                        {draftQuery.error instanceof Error
                          ? draftQuery.error.message
                          : 'Failed to load draft'}
                      </Alert>
                    ) : draftQuery.data ? (
                      <>
                        {!canEdit ? (
                          <Alert severity="info">
                            Publishing drafts requires deployment editor access or higher.
                          </Alert>
                        ) : null}
                        <Stack spacing={0.5}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {draftQuery.data.id}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            Revision {draftQuery.data.revisionNumber} · {draftQuery.data.status}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Updated {formatTimestamp(draftQuery.data.updatedAt)}
                          </Typography>
                        </Stack>
                        <Button
                          variant="contained"
                          startIcon={<PublishRoundedIcon />}
                          disabled={publishMutation.isPending || !canEdit}
                          onClick={() => publishMutation.mutate(draftQuery.data!.id)}
                        >
                          {publishMutation.isPending ? 'Publishing...' : 'Publish draft'}
                        </Button>
                        {publishMutation.isError ? (
                          <Alert severity="error">
                            {publishMutation.error instanceof Error
                              ? publishMutation.error.message
                              : 'Failed to publish draft'}
                          </Alert>
                        ) : null}
                      </>
                    ) : null}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={8}>
              <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h6">Draft snapshot</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        This is the shape of the editable config that will be compiled into immutable
                        deployment artifacts on publish.
                      </Typography>
                    </Box>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Actions
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.actions}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Entity spaces
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.entitySpaces}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Routed actions
                            </Typography>
                            <Typography variant="h5" sx={{ fontWeight: 800 }}>
                              {draftSummary.routingActions}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Typography variant="overline" color="text.secondary">
                              Authz mode
                            </Typography>
                            <Typography variant="body1" sx={{ fontWeight: 700 }}>
                              {draftSummary.authzMode}
                            </Typography>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Divider />

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={`LLM: ${draftSummary.llmProvider}`} variant="outlined" />
                      <Chip label={`Vector: ${draftSummary.vectorStrategy}`} variant="outlined" />
                      <Chip label={`Admin key: ${draftSummary.adminApiKeyEnabled}`} variant="outlined" />
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
                  <Typography variant="h6">Configuration diff center</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Compare the editable draft against the latest published version, current live state,
                    and the selected template/source inputs before you publish or apply.
                  </Typography>
                </Box>

                {diffCenterQuery.isLoading ? (
                  <Typography color="text.secondary">Loading configuration diff...</Typography>
                ) : diffCenterQuery.isError ? (
                  <Alert severity="error">
                    {diffCenterQuery.error instanceof Error
                      ? diffCenterQuery.error.message
                      : 'Failed to load configuration diff center'}
                  </Alert>
                ) : diffCenterQuery.data ? (
                  <>
                    <Alert severity="info">{diffCenterQuery.data.summaryMessage}</Alert>

                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} flexWrap="wrap" useFlexGap>
                      {[diffCenterQuery.data.draft, diffCenterQuery.data.latestPublished, diffCenterQuery.data.live].map((reference) => (
                        <Chip
                          key={`${reference.stage}:${reference.referenceId ?? 'none'}`}
                          color={referenceChipColor(reference)}
                          variant={reference.available ? 'filled' : 'outlined'}
                          label={
                            reference.available
                              ? `${reference.stage.replace('_', ' ')} · ${reference.referenceLabel}`
                              : `${reference.stage.replace('_', ' ')} · ${reference.referenceLabel}`
                          }
                        />
                      ))}
                    </Stack>

                    <Grid container spacing={2}>
                      <Grid item xs={12} lg={5}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Template and source inputs
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {diffCenterQuery.data.templateSource.templateDescription}
                              </Typography>
                              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                <Chip label={diffCenterQuery.data.templateSource.templateName} variant="outlined" />
                                <Chip label={`Repo: ${diffCenterQuery.data.templateSource.repository}`} variant="outlined" />
                                <Chip label={`Branch: ${diffCenterQuery.data.templateSource.branch}`} variant="outlined" />
                                <Chip label={`LLM: ${diffCenterQuery.data.templateSource.llmProvider}`} variant="outlined" />
                                <Chip label={`Embeddings: ${diffCenterQuery.data.templateSource.embeddingProvider}`} variant="outlined" />
                                <Chip label={`Vector: ${diffCenterQuery.data.templateSource.vectorStrategy}`} variant="outlined" />
                                <Chip label={`Runtime: ${diffCenterQuery.data.templateSource.runtimeProfile}`} variant="outlined" />
                                <Chip label={`Connector: ${diffCenterQuery.data.templateSource.connectorProfile}`} variant="outlined" />
                                {diffCenterQuery.data.templateSource.overrideActive ? (
                                  <Chip label="Source override active" color="secondary" />
                                ) : (
                                  <Chip label="Using platform default source" variant="outlined" />
                                )}
                              </Stack>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} lg={7}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                Section drift summary
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                Each row shows whether a config domain is aligned, published but not live,
                                or still only present in the editable draft.
                              </Typography>
                              <Table size="small">
                                <TableHead>
                                  <TableRow>
                                    <TableCell>Section</TableCell>
                                    <TableCell>Draft</TableCell>
                                    <TableCell>Latest published</TableCell>
                                    <TableCell>Live</TableCell>
                                    <TableCell>Drift</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {diffCenterQuery.data.sections.map((section) => (
                                    <TableRow key={section.key} hover>
                                      <TableCell>
                                        <Stack spacing={0.5}>
                                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                            {section.label}
                                          </Typography>
                                          <Typography variant="caption" color="text.secondary">
                                            {section.summaryMessage}
                                          </Typography>
                                        </Stack>
                                      </TableCell>
                                      <TableCell sx={{ maxWidth: 240 }}>{section.draftValue}</TableCell>
                                      <TableCell sx={{ maxWidth: 240 }}>{section.latestPublishedValue}</TableCell>
                                      <TableCell sx={{ maxWidth: 240 }}>{section.liveValue}</TableCell>
                                      <TableCell>
                                        <Chip
                                          size="small"
                                          color={diffStateColor(section.driftState)}
                                          variant="outlined"
                                          label={section.driftState.replace(/_/g, ' ')}
                                        />
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
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
                  <Typography variant="h6">Published versions</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Publishing compiles the draft into immutable artifacts and rotates the active draft
                    to the next revision.
                  </Typography>
                </Box>
                {versionsQuery.isLoading ? (
                  <Typography color="text.secondary">Loading versions...</Typography>
                ) : versionsQuery.isError ? (
                  <Alert severity="error">
                    {versionsQuery.error instanceof Error
                      ? versionsQuery.error.message
                      : 'Failed to load versions'}
                  </Alert>
                ) : (
                  <>
                    {!canOperate ? (
                      <Alert severity="info">
                        Applying versions requires deployment operator access or higher.
                      </Alert>
                    ) : null}
                    <Grid container spacing={1.5}>
                      <Grid item xs={12} md={4}>
                        <TextField
                          fullWidth
                          label="Search versions"
                          value={searchTerm}
                          onChange={(event) => setSearchTerm(event.target.value)}
                          helperText="Matches label, id, hash, release, or error details"
                        />
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          fullWidth
                          select
                          label="Version status"
                          value={versionStatusFilter}
                          onChange={(event) => setVersionStatusFilter(event.target.value)}
                        >
                          <MenuItem value="ALL">All version states</MenuItem>
                          <MenuItem value="PUBLISHED">Published</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={3}>
                        <TextField
                          fullWidth
                          select
                          label="Latest release"
                          value={releaseStatusFilter}
                          onChange={(event) => setReleaseStatusFilter(event.target.value)}
                        >
                          <MenuItem value="ALL">All release states</MenuItem>
                          <MenuItem value="UNAPPLIED">Unapplied</MenuItem>
                          <MenuItem value="APPLY_REQUESTED">Apply requested</MenuItem>
                          <MenuItem value="PRE_APPLY_VERIFYING">Pre-apply verifying</MenuItem>
                          <MenuItem value="PRE_APPLY_BLOCKED">Pre-apply blocked</MenuItem>
                          <MenuItem value="PROVISIONING">Provisioning</MenuItem>
                          <MenuItem value="VERIFYING">Verifying</MenuItem>
                          <MenuItem value="APPLIED_VERIFIED">Applied verified</MenuItem>
                          <MenuItem value="APPLIED_VERIFICATION_FAILED">Verification failed</MenuItem>
                          <MenuItem value="FAILED">Failed</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid item xs={12} md={2}>
                        <TextField
                          fullWidth
                          select
                          label="Reindex"
                          value={reindexFilter}
                          onChange={(event) => setReindexFilter(event.target.value)}
                        >
                          <MenuItem value="ALL">All</MenuItem>
                          <MenuItem value="REINDEX_REQUIRED">Required</MenuItem>
                          <MenuItem value="READY">Ready</MenuItem>
                        </TextField>
                      </Grid>
                    </Grid>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip label={`Visible versions: ${filteredVersions.length}`} variant="outlined" />
                      <Chip label={`Visible releases: ${filteredReleaseHistory.length}`} variant="outlined" />
                    </Stack>
                    <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Version</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Config hash</TableCell>
                        <TableCell>Reindex</TableCell>
                        <TableCell>Published</TableCell>
                        <TableCell align="right">Action</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredVersions.map((version) => (
                        <TableRow
                          key={version.id}
                          hover
                          selected={version.id === selectedVersionId}
                          onClick={() => setSelectedVersionId(version.id)}
                          sx={{ cursor: 'pointer' }}
                        >
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {version.versionLabel}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {version.id}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color="primary"
                              icon={<CheckCircleRoundedIcon />}
                              label={version.status}
                            />
                          </TableCell>
                          <TableCell sx={{ fontFamily: 'monospace' }}>
                            {version.configHash.slice(0, 12)}...
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={version.reindexRequired ? 'warning' : 'success'}
                              icon={version.reindexRequired ? <WarningAmberRoundedIcon /> : <CheckCircleRoundedIcon />}
                              label={version.reindexRequired ? 'Required' : 'No'}
                            />
                          </TableCell>
                          <TableCell>{formatTimestamp(version.publishedAt)}</TableCell>
                          <TableCell align="right">
                            <Button
                              variant="outlined"
                              size="small"
                              startIcon={<RocketLaunchRoundedIcon />}
                              disabled={!canOperate || applyMutation.isPending || Boolean(inProgressRelease)}
                              onClick={(event) => {
                                event.stopPropagation()
                                if (!isPlatformAdmin && selectedDeployment.approvalRequiredForApply) {
                                  navigate(
                                    `/approvals?deploymentId=${encodeURIComponent(selectedDeployment.id)}&action=APPLY_VERSION&versionId=${encodeURIComponent(version.id)}`,
                                  )
                                  return
                                }
                                applyMutation.mutate({
                                  deploymentId: selectedDeployment.id,
                                  versionId: version.id,
                                })
                              }}
                            >
                              {inProgressRelease
                                ? 'Release running'
                                : !isPlatformAdmin && selectedDeployment.approvalRequiredForApply
                                  ? 'Request approval'
                                  : 'Apply'}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                    </Table>
                    {filteredVersions.length === 0 ? (
                      <Alert severity="info">No published versions match the current filters.</Alert>
                    ) : null}
                  </>
                )}
                {applyMutation.isError ? (
                  <Alert severity="error">
                    {applyMutation.error instanceof Error
                      ? applyMutation.error.message
                      : 'Failed to apply version'}
                  </Alert>
                ) : null}
                {!isPlatformAdmin && selectedDeployment?.approvalRequiredForApply ? (
                  <Alert severity="warning">
                    This deployment requires approval before operators can apply versions. Use the request flow from
                    the apply button or from the Approvals section.
                  </Alert>
                ) : null}
                {applyMutation.isSuccess ? (
                  <Alert severity="info">
                    Apply queued: {applyMutation.data.currentStepDescription ?? applyMutation.data.status}
                  </Alert>
                ) : null}
                {inProgressRelease ? (
                  <Alert severity="info">
                    A release is already running for this deployment. Status and current step refresh automatically.
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Latest release execution</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Applies now run asynchronously. This card shows the newest release, its current
                    step, and any error that blocked the rollout.
                  </Typography>
                </Box>
                {latestRelease ? (
                  <>
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip label={latestRelease.status} color={releaseStatusColor(latestRelease.status)} />
                      <Chip
                        label={`Provisioning: ${latestRelease.provisioningStatus}`}
                        color={provisioningStatusColor(latestRelease.provisioningStatus)}
                        variant="outlined"
                      />
                      <Chip
                        label={`Verification: ${latestRelease.verificationStatus}`}
                        color={verificationStatusColor(latestRelease.verificationStatus)}
                        variant="outlined"
                      />
                    </Stack>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Stack spacing={1}>
                          <Typography variant="body2">
                            Current step: <strong>{latestRelease.currentStepDescription ?? 'Not recorded yet'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Release: <strong>{latestRelease.id}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Version: <strong>{latestRelease.deploymentVersionId}</strong>
                          </Typography>
                        </Stack>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Stack spacing={1}>
                          <Typography variant="body2">
                            Updated: <strong>{formatOptionalTimestamp(latestRelease.updatedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Applied: <strong>{formatOptionalTimestamp(latestRelease.appliedAt)}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Verification run: <strong>{latestRelease.verificationRunId ?? 'Pending'}</strong>
                          </Typography>
                          <Typography variant="body2">
                            Railway project:{' '}
                            {latestRailwayProjectUrl ? (
                              <Link
                                href={latestRailwayProjectUrl}
                                target="_blank"
                                rel="noreferrer"
                                underline="hover"
                              >
                                {latestRailwayProjectName ?? 'Open project'}
                              </Link>
                            ) : (
                              <strong>{latestRailwayProjectName ?? 'Not available yet'}</strong>
                            )}
                          </Typography>
                          <Typography variant="body2">
                            Runtime Swagger:{' '}
                            {runtimeSwaggerUrl ? (
                              <Link href={runtimeSwaggerUrl} target="_blank" rel="noreferrer" underline="hover">
                                Open docs
                              </Link>
                            ) : (
                              <strong>Not available yet</strong>
                            )}
                          </Typography>
                          <Typography variant="body2">
                            Connector admin via runtime:{' '}
                            {connectorAdminUrl ? (
                              <Link href={connectorAdminUrl} target="_blank" rel="noreferrer" underline="hover">
                                Open admin overview
                              </Link>
                            ) : (
                              <strong>Not available yet</strong>
                            )}
                          </Typography>
                          {integrationSummary ? (
                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                              <Chip
                                label={integrationModeLabel(integrationSummary)}
                                color={integrationModeColor(integrationSummary)}
                                size="small"
                                variant="outlined"
                              />
                              {integrationSummary.runtimeAuthMode ? (
                                <Chip label={`Auth: ${integrationSummary.runtimeAuthMode}`} size="small" variant="outlined" />
                              ) : null}
                            </Stack>
                          ) : null}
                        </Stack>
                      </Grid>
                    </Grid>
                    {activationUnconfirmed ? (
                      <Alert
                        severity="warning"
                        action={canOperate ? (
                          <Button
                            color="inherit"
                            size="small"
                            disabled={reconcileReleaseMutation.isPending}
                            onClick={() => {
                              void reconcileReleaseMutation.mutateAsync(selectedDeploymentId)
                            }}
                          >
                            {reconcileReleaseMutation.isPending ? 'Refreshing...' : 'Refresh status'}
                          </Button>
                        ) : undefined}
                      >
                        Deployment activation status is not confirmed yet. Railway may still be finishing startup.
                        {latestRelease.errorMessage ? ` ${latestRelease.errorMessage}` : ''}
                      </Alert>
                    ) : latestRelease.errorMessage ? (
                      <Alert severity="error">{latestRelease.errorMessage}</Alert>
                    ) : null}
                  </>
                ) : (
                  <Alert severity="info">No release has been applied yet for this deployment.</Alert>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">Railway plan preview</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    This preview shows what the platform would send to Railway for the selected version:
                    project naming, service roots, env vars, immutable artifact URLs, and rollout steps.
                  </Typography>
                </Box>

                {selectedVersion ? (
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={selectedVersion.versionLabel} color="primary" />
                    <Chip label={selectedVersion.id} variant="outlined" />
                    <Chip label={selectedVersion.configHash.slice(0, 12)} variant="outlined" />
                  </Stack>
                ) : (
                  <Alert severity="info">Publish a version first to inspect the Railway plan.</Alert>
                )}

                {railwayPlanQuery.isLoading ? (
                  <Typography color="text.secondary">Loading Railway plan...</Typography>
                ) : railwayPlanQuery.isError ? (
                  <Alert severity="error">
                    {railwayPlanQuery.error instanceof Error
                      ? railwayPlanQuery.error.message
                      : 'Failed to load Railway plan'}
                  </Alert>
                ) : liveRailwayPlanQuery.isLoading ? (
                  <Typography color="text.secondary">Comparing against the current live release plan...</Typography>
                ) : liveRailwayPlanQuery.isError ? (
                  <Alert severity="error">
                    {liveRailwayPlanQuery.error instanceof Error
                      ? liveRailwayPlanQuery.error.message
                      : 'Failed to load the current live release plan'}
                  </Alert>
                ) : plan ? (
                  <>
                    {impactMessage ? (
                      <Alert severity={livePlan ? (runtimeImpact?.changed || connectorImpact?.changed || changedArtifacts.length > 0 ? 'info' : 'success') : 'warning'}>
                        <strong>Release impact</strong>: {impactMessage}
                      </Alert>
                    ) : null}

                    {runtimeImpact && connectorImpact ? (
                      <Grid container spacing={2}>
                        <Grid item xs={12} md={3}>
                          <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                            <CardContent>
                              <Stack spacing={1}>
                                <Typography variant="overline" color="text.secondary">
                                  Runtime service
                                </Typography>
                                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                  {runtimeImpact.changed ? 'Will change' : 'No change'}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {runtimeImpact.env.changedEntries.length} env reference(s), root, Dockerfile, and public URL checks included.
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  {runtimeImpact.rootDirChanged ? <Chip size="small" label="Root dir" color="warning" variant="outlined" /> : null}
                                  {runtimeImpact.dockerfileChanged ? <Chip size="small" label="Dockerfile" color="warning" variant="outlined" /> : null}
                                  {runtimeImpact.baseUrlChanged ? <Chip size="small" label="Public URL" color="warning" variant="outlined" /> : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                            <CardContent>
                              <Stack spacing={1}>
                                <Typography variant="overline" color="text.secondary">
                                  REST connector
                                </Typography>
                                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                  {connectorImpact.changed ? 'Will change' : 'No change'}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {connectorImpact.env.changedEntries.length} env reference(s), routing links, and runtime-backed operational proxy configuration included.
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  {connectorImpact.rootDirChanged ? <Chip size="small" label="Root dir" color="warning" variant="outlined" /> : null}
                                  {connectorImpact.dockerfileChanged ? <Chip size="small" label="Dockerfile" color="warning" variant="outlined" /> : null}
                                  {connectorImpact.baseUrlChanged ? <Chip size="small" label="Internal URL" color="warning" variant="outlined" /> : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                            <CardContent>
                              <Stack spacing={1}>
                                <Typography variant="overline" color="text.secondary">
                                  Artifact links
                                </Typography>
                                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                  {changedArtifacts.length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  Immutable artifact URLs that will point to a different version after apply.
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  {changedArtifacts.length > 0
                                    ? changedArtifacts.map((artifact) => (
                                      <Chip key={artifact} size="small" label={artifact} color="warning" variant="outlined" />
                                    ))
                                    : <Chip size="small" label="No artifact URL change" color="success" variant="outlined" />}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                        <Grid item xs={12} md={3}>
                          <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                            <CardContent>
                              <Stack spacing={1}>
                                <Typography variant="overline" color="text.secondary">
                                  Deployment links
                                </Typography>
                                <Typography variant="h6" sx={{ fontWeight: 800 }}>
                                  {livePlan ? 'Compared to live' : 'First release'}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  Runtime link changes and internal connector surface changes are included in the impact review.
                                </Typography>
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                  <Chip
                                    size="small"
                                    label={selectedDeployment?.runtimeBaseUrl ? (plan.services.runtime.baseUrl === selectedDeployment.runtimeBaseUrl ? 'Runtime URL unchanged' : 'Runtime URL changes') : 'Runtime URL will be created'}
                                    color={selectedDeployment?.runtimeBaseUrl && plan.services.runtime.baseUrl === selectedDeployment.runtimeBaseUrl ? 'success' : 'warning'}
                                    variant="outlined"
                                  />
                                  <Chip
                                    size="small"
                                    label={selectedDeployment?.connectorBaseUrl ? (plan.services.restConnector.baseUrl === selectedDeployment.connectorBaseUrl ? 'Connector internal URL unchanged' : 'Connector internal URL changes') : 'Connector internal URL will be created'}
                                    color={selectedDeployment?.connectorBaseUrl && plan.services.restConnector.baseUrl === selectedDeployment.connectorBaseUrl ? 'success' : 'warning'}
                                    variant="outlined"
                                  />
                                  {integrationSummary ? (
                                    <Chip
                                      size="small"
                                      label={integrationModeLabel(integrationSummary)}
                                      color={integrationModeColor(integrationSummary)}
                                      variant="outlined"
                                    />
                                  ) : null}
                                </Stack>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      </Grid>
                    ) : null}

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Runtime env impact</Typography>
                              {runtimeImpact && runtimeImpact.env.changedEntries.length > 0 ? (
                                renderEnvImpactTable(runtimeImpact.env.changedEntries)
                              ) : (
                                <Alert severity="success">Runtime env references are unchanged for this apply.</Alert>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">REST connector env impact</Typography>
                              {connectorImpact && connectorImpact.env.changedEntries.length > 0 ? (
                                renderEnvImpactTable(connectorImpact.env.changedEntries)
                              ) : (
                                <Alert severity="success">Internal REST connector env references are unchanged for this apply.</Alert>
                              )}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Divider />

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                Project
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.projectName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.repository} · {plan.branch}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                Mode: {plan.mode}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                Runtime service
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.services.runtime.serviceName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.services.runtime.rootDir ?? 'repo root'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                Dockerfile: {plan.services.runtime.dockerfilePath ?? 'default'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {plan.services.runtime.baseUrl}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={4}>
                        <Card sx={{ height: '100%', border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1}>
                              <Typography variant="overline" color="text.secondary">
                                REST connector service
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 700 }}>
                                {plan.services.restConnector.serviceName}
                              </Typography>
                              <Typography variant="body2" color="text.secondary">
                                {plan.services.restConnector.rootDir ?? 'repo root'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                Dockerfile: {plan.services.restConnector.dockerfilePath ?? 'default'}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {plan.services.restConnector.baseUrl}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Artifact bundle</Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                actions: {plan.artifactUrls.actions}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                entities: {plan.artifactUrls.entities}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                routing: {plan.artifactUrls.routing}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                prompts: {plan.artifactUrls.prompts}
                              </Typography>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                manifest: {plan.artifactUrls.manifest}
                              </Typography>
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Rollout steps</Typography>
                              {plan.steps.map((step) => (
                                <Typography key={step.key} variant="body2">
                                  {step.order}. {step.description}
                                </Typography>
                              ))}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">Runtime env</Typography>
                              {renderEnvTable(plan.services.runtime.env)}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
                          <CardContent>
                            <Stack spacing={1.5}>
                              <Typography variant="h6">REST connector env</Typography>
                              {renderEnvTable(plan.services.restConnector.env)}
                            </Stack>
                          </CardContent>
                        </Card>
                      </Grid>
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
                  <Typography variant="h6">Release history</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Releases now capture provisioning status and verification linkage, so this table is
                    the first support-ready rollout evidence view.
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
                  <Alert severity="info">No release history exists yet for this deployment.</Alert>
                ) : filteredReleaseHistory.length === 0 ? (
                  <Alert severity="info">No releases match the current filters.</Alert>
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
                      {filteredReleaseHistory.map((release) => (
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
                                color={provisioningStatusColor(release.provisioningStatus)}
                                variant="outlined"
                              />
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 320 }}>
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
                                color={verificationStatusColor(release.verificationStatus)}
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
        </>
      ) : null}
    </Stack>
  )
}
