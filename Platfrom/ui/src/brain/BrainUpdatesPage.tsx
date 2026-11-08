import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import HistoryRounded from '@mui/icons-material/HistoryRounded'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import PublishRounded from '@mui/icons-material/PublishRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import RocketLaunchRounded from '@mui/icons-material/RocketLaunchRounded'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import HubOutlined from '@mui/icons-material/HubOutlined'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Checkbox, Chip,
  Divider, Drawer, FormControlLabel, IconButton, LinearProgress, ListItemButton, MenuItem, Stack,
  Step, StepLabel, Stepper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom'
import {
  applyDeploymentVersion, fetchCompatibleDeploymentSourceArtifacts, fetchDeploymentAiBehaviourProfiles, fetchDeploymentConfigDiffCenter,
  fetchDeploymentDraft, fetchDeploymentReleases, fetchDeploymentTargetContext,
  fetchDeploymentVerificationRuns, fetchDeploymentVersionManifest, fetchDeploymentVersions, fetchDeploymentTargetOptions, fetchDeploymentTargetProfiles,
  publishDeploymentDraft, reconcileDeploymentRelease, rerunDeploymentVerification, validateDeploymentDraft,
  type DeploymentConfigDiffCenterSummary, type DeploymentDraftResponse, type DeploymentReleaseSummary,
  type DeploymentVersionSummary, type AiBehaviourCatalogue,
} from '../api/platformApi'
import { promptConfigIdentity as stableConfig, readAiBehaviour } from '../aiBehaviourProfiles'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { RevisionsPage } from '../pages/RevisionsPage'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainCustomerReleaseTasks } from './BrainCustomerReleaseTasks'
import { brainPath } from './brainNavigation'
import { BrainReleaseVersions } from './BrainReleaseVersions'
import { environmentLabel, releaseInProgress, targetReleaseState } from './brainReleasePresentation'
import { buildChangePreview, configurationFieldChanges, previewHeadline, sectionPreviewBaseline, type PreviewBaseline } from './brainChangePreview'
import { BrainChangeComparison } from './BrainChangeComparison'

const sections = [
  ['actionsConfig', 'AI capabilities', 'actions'],
  ['entityConfig', 'Knowledge collections', 'entities'],
  ['routingConfig', 'Capability connections', 'routing'],
  ['providerConfig', 'AI models & storage', 'providers'],
  ['securityConfig', 'Access & permissions', 'security'],
  ['promptConfig', 'Responses', 'prompts'],
  ['knowledgeSourceConfig', 'Knowledge sources', 'knowledgeSources'],
  ['shellConfig', 'Channels', 'shell'],
  ['marketplaceDatasetConfig', 'Connected datasets', 'marketplaceDatasets'],
  ['behaviorConfig', 'AI behaviour', 'behavior'],
] as const

type Change = { key: string; label: string; current: string; next: string; changed: boolean | null }
type Review = { mode: 'publish-update' | 'publish-only' | 'apply'; draftStamp: string; confirmed: boolean; versionId?: string; appliedReleaseId?: string | null }

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

export { stableConfig }
export { configurationFieldChanges }

export function draftStamp(draft: DeploymentDraftResponse | undefined): string {
  return draft ? `${draft.id}:${draft.updatedAt}:${stableConfig(sections.map(([key]) => draft[key]))}` : ''
}

export function updatesReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return releaseInProgress(release)
}

function summary(value: unknown): string {
  if (value === undefined) return 'Unavailable'
  if (value === null) return 'Not configured'
  if (Array.isArray(value)) return `${value.length} entries`
  if (record(value)) return Object.keys(value).length ? `${Object.keys(value).length} settings` : 'Not configured'
  return 'Configured'
}

function responseSummary(value: unknown, catalogue?: AiBehaviourCatalogue): string | null {
  const metadata = readAiBehaviour(value)
  if (!metadata || !catalogue) return null
  return catalogue.groups.map(group => `${group.label}: ${group.options.find(option => option.id === metadata[group.key]?.presetId)?.label ?? 'Custom'}`).join('; ')
}

// Only compare the server's deployment-wide summaries when their version is this exact target's version.
export function buildUpdateChanges(
  draft: DeploymentDraftResponse | undefined,
  manifest: unknown,
  appliedVersionId: string | null,
  diff?: DeploymentConfigDiffCenterSummary,
  catalogue?: AiBehaviourCatalogue,
): Change[] {
  if (!draft) return []
  const snapshot = record(manifest) && manifest.versionId === appliedVersionId && manifest.deploymentId === draft.deploymentId ? manifest : null
  const serverMatches = diff?.live.referenceId === appliedVersionId && diff?.draft.referenceId === draft.id
    && diff?.draft.updatedAt === draft.updatedAt
  return sections.map(([key, label, serverKey]) => {
    const server = serverMatches ? diff?.sections.find(item => item.key === serverKey) : undefined
    const available = !appliedVersionId || Boolean(snapshot && Object.prototype.hasOwnProperty.call(snapshot, key))
    const changed = server ? !server.draftMatchesLive
      : available ? !appliedVersionId || stableConfig(snapshot?.[key]) !== stableConfig(draft[key]) : null
    return { key, label, changed,
      current: (key === 'promptConfig' ? responseSummary(snapshot?.[key], catalogue) : null)
        ?? server?.liveValue ?? (!appliedVersionId ? 'Not applied' : summary(snapshot?.[key])),
      next: (key === 'promptConfig' ? responseSummary(draft[key], catalogue) : null) ?? server?.draftValue ?? summary(draft[key]) }
  })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'The operation could not be completed.'
}

function statusColor(status: string): 'success' | 'error' | 'warning' | 'info' | 'default' {
  if (['APPLIED_VERIFIED', 'PASSED'].includes(status)) return 'success'
  if (['FAILED', 'PRE_APPLY_BLOCKED'].includes(status)) return 'error'
  if (status === 'APPLIED_VERIFICATION_FAILED') return 'warning'
  if (['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING', 'RUNNING'].includes(status)) return 'info'
  return 'default'
}

function formatTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : 'Not recorded'
}

function ChangeTable({ changes, nextLabel = 'Saved draft', maxHeight }: { changes: Change[]; nextLabel?: string; maxHeight?: number }) {
  return <TableContainer sx={{ minHeight: 0, maxHeight, overflow: 'auto' }}>
    <Table size="small" stickyHeader aria-label="Saved draft compared with applied target" sx={{ tableLayout: 'fixed' }}>
      <TableHead><TableRow><TableCell sx={{ width: '34%' }}>Area</TableCell><TableCell>Current</TableCell><TableCell>{nextLabel}</TableCell></TableRow></TableHead>
      <TableBody>{changes.map(item => <TableRow key={item.key}>
        <TableCell sx={{ fontWeight: 600 }}>{item.label}</TableCell>
        <TableCell>{item.current}</TableCell>
        <TableCell><Stack spacing={0.25}><Typography variant="body2">{item.next}</Typography>
          <Typography variant="caption" color={item.changed === null ? 'warning.main' : 'text.secondary'}>
            {item.changed === null ? 'Comparison unavailable' : item.changed ? 'Changed' : 'Unchanged'}
          </Typography></Stack></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </TableContainer>
}

export function outcomeSummary(key: string, value: unknown, catalogue?: AiBehaviourCatalogue): string {
  if (key === 'promptConfig') return responseSummary(value, catalogue) ?? (record(value) && Object.keys(value).length ? 'Reply preferences and instructions' : summary(value))
  if (!record(value)) return summary(value)
  const list = key === 'actionsConfig' ? value.actions : key === 'entityConfig' ? value.entities : key === 'knowledgeSourceConfig' ? value.sources : null
  if (Array.isArray(list)) {
    const names = list.filter(record).map(item => typeof item.displayName === 'string' ? item.displayName : '').filter(Boolean)
    const noun = key === 'actionsConfig' ? list.length === 1 ? 'capability' : 'capabilities'
      : key === 'entityConfig' ? list.length === 1 ? 'collection' : 'collections' : list.length === 1 ? 'source' : 'sources'
    const remaining = list.length - Math.min(names.length, 2)
    return names.length ? `${names.slice(0, 2).join(', ')}${remaining ? ` +${remaining}` : ''}` : `${list.length} ${noun}`
  }
  const labels: Record<string, string> = { providerConfig: 'AI models, embeddings and search', securityConfig: 'Access and conversation safeguards',
    routingConfig: 'Action destinations and connections', behaviorConfig: 'Conversation and execution settings', shellConfig: 'Customer workspace settings' }
  return Object.keys(value).length && labels[key] ? labels[key] : summary(value)
}

function OutcomeChanges({ changes, config, currentConfig, catalogue, baseline = 'applied', onSelect }: {
  changes: Change[]; config: unknown; currentConfig: unknown; catalogue?: AiBehaviourCatalogue; baseline?: PreviewBaseline; onSelect?: (key: string) => void
}) {
  return <Stack divider={<Divider />} data-update-outcomes>{changes.map(item => {
    const itemBaseline = sectionPreviewBaseline(currentConfig, item.key, baseline)
    const preview = buildChangePreview(item.key, record(currentConfig) ? currentConfig[item.key] : undefined, record(config) ? config[item.key] : undefined,
      item.changed === null ? 'unavailable' : itemBaseline, catalogue)
    const runtimeOnly = !preview.fieldChanges.length && preview.runtimeFieldChanges.length > 0
    const headline = preview.baseline === 'unavailable' ? previewHeadline(preview) ?? 'Applied comparison unavailable'
      : item.changed === false ? 'No changes' : runtimeOnly ? 'Saved reply inputs match · runtime guidance differs' : previewHeadline(preview)
    const Icon = ['entityConfig', 'knowledgeSourceConfig', 'marketplaceDatasetConfig'].includes(item.key) ? LayersOutlined
      : item.key === 'promptConfig' ? ChatBubbleOutlineRounded : item.key === 'actionsConfig' ? AutoAwesomeOutlined : HubOutlined
    const content = <>
      <Box sx={{ width: 34, height: 34, borderRadius: 1, bgcolor: 'action.hover', display: 'grid', placeItems: 'center', flexShrink: 0 }}><Icon sx={{ fontSize: 21 }} /></Box>
      <Box sx={{ flex: 1, minWidth: 0 }}><Typography fontSize={14} fontWeight={600}>{preview.title}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
          {headline ?? (item.changed === false ? 'No changes' : 'Settings updated · inspect details')}
        </Typography></Box>
      <Typography variant="caption" color={preview.baseline === 'unavailable' ? 'warning.main' : 'text.secondary'} sx={{ flexShrink: 0 }}>
        {preview.baseline === 'unavailable' ? 'Unavailable' : baseline === 'new' ? 'New' : item.changed && preview.fieldChanges.length ? `${preview.fieldChanges.length} ${preview.fieldChanges.length === 1 ? 'field' : 'fields'}` : runtimeOnly ? 'Runtime details' : item.changed ? 'Changed' : 'Unchanged'}
      </Typography>
      {onSelect && <ChevronRightRounded fontSize="small" color="action" />}
    </>
    return onSelect ? <ListItemButton key={item.key} aria-label={`Preview ${preview.title}`} onClick={() => onSelect(item.key)} sx={{ px: 0.5, py: 1.5, gap: 1.5, alignItems: 'center' }}>{content}</ListItemButton>
      : <Stack key={item.key} direction="row" gap={1.5} alignItems="center" sx={{ py: 1.25 }}>{content}</Stack>
  })}</Stack>
}

export function BrainUpdatesPage() {
  const { brainId, targetProfileId } = useBrainWorkspace()
  return <UpdatesWorkspace key={`${brainId}:${targetProfileId}`} />
}

function UpdatesWorkspace() {
  const { brainId, targetProfileId, targetProfile, release: appliedRelease, target, href } = useBrainWorkspace()
  const { workspace, selectedDeploymentId } = useDeploymentWorkspace()
  const access = selectedDeploymentId === brainId ? workspace?.access : null
  const canEdit = access?.canEdit ?? false
  const canOperate = access?.canOperate ?? false
  const auth = usePlatformAuth()
  const customer = auth.session?.role === 'CUSTOMER_ADMIN'
  const navigate = useNavigate()
  const location = useLocation()
  const client = useQueryClient()
  const requestedPanel = new URLSearchParams(location.search).get('panel')
  const staging = targetProfile?.environmentName === 'staging'
  const production = targetProfile?.environmentName === 'production'
  const panelUnavailable = customer && Boolean(requestedPanel && (
    (['connections', 'assignment'].includes(requestedPanel) && !staging)
    || (requestedPanel === 'promotion' && !production)))
  const view = requestedPanel && ['history', 'versions', 'technical'].includes(requestedPanel) ? requestedPanel
    : customer && !panelUnavailable && requestedPanel && ['connections', 'assignment', 'promotion'].includes(requestedPanel)
      ? requestedPanel : 'review'
  const changeView = (next: string) => {
    const params = new URLSearchParams(location.search)
    if (next === 'review') params.delete('panel'); else params.set('panel', next)
    navigate({ pathname: location.pathname, search: params.toString() }, { replace: true })
  }
  const [review, setReview] = useState<Review | null>(null)
  const [published, setPublished] = useState<DeploymentVersionSummary | null>(null)
  const [selectedVersion, setSelectedVersion] = useState<DeploymentVersionSummary | null>(null)
  const publishedConfiguration = useRef('')
  const [sourceArtifactId, setSourceArtifactId] = useState('')
  const [detailKey, setDetail] = useState<string | null>(null)
  const [versionDetail, setVersionDetail] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const operation = useRef<{ identity: string; key: string } | null>(null)
  const operationRunning = useRef(false)

  const draft = useQuery({ queryKey: ['deployment-draft', brainId], queryFn: () => fetchDeploymentDraft(brainId), enabled: Boolean(brainId) })
  const diff = useQuery({ queryKey: ['deployment-config-diff-center', brainId], queryFn: () => fetchDeploymentConfigDiffCenter(brainId), enabled: Boolean(brainId) })
  const catalogue = useQuery({ queryKey: ['deployment-ai-behaviour-profiles'], queryFn: fetchDeploymentAiBehaviourProfiles })
  const versions = useQuery({ queryKey: ['deployment-versions', brainId], queryFn: () => fetchDeploymentVersions(brainId), enabled: Boolean(brainId) })
  const releases = useQuery({ queryKey: ['deployment-releases', brainId], queryFn: () => fetchDeploymentReleases(brainId), enabled: Boolean(brainId),
    refetchInterval: query => query.state.data?.some(updatesReleaseInProgress) ? 3000 : false })
  const verification = useQuery({ queryKey: ['deployment-verification-runs', brainId], queryFn: () => fetchDeploymentVerificationRuns(brainId), enabled: Boolean(brainId),
    refetchInterval: () => releases.data?.some(updatesReleaseInProgress) ? 3000 : false })
  const targetContext = useQuery({ queryKey: ['brain-updates-target', brainId, targetProfileId, target?.releaseId],
    queryFn: () => fetchDeploymentTargetContext(target!), enabled: Boolean(target) })
  const appliedVersionId = appliedRelease?.deploymentVersionId ?? null
  const appliedManifest = useQuery({ queryKey: ['deployment-version-manifest', brainId, appliedVersionId],
    queryFn: () => fetchDeploymentVersionManifest(brainId, appliedVersionId!), enabled: Boolean(appliedVersionId) })
  const publishedMatchesDraft = selectedDeploymentId === brainId && workspace?.lifecycle.savedDraftMatchesLatestPublished
    && workspace.draft.id === draft.data?.id && workspace.draft.updatedAt === draft.data?.updatedAt
  const publishedStillMatchesDraft = published && publishedConfiguration.current === stableConfig(sections.map(([key]) => draft.data?.[key]))
  const continuation = publishedStillMatchesDraft ? published : publishedMatchesDraft ? versions.data?.find(item => item.id === workspace?.lifecycle.latestPublishedVersionId) ?? null : null
  const candidate = review?.mode === 'apply' || versionDetail ? selectedVersion ?? continuation : continuation
  const candidateManifest = useQuery({ queryKey: ['deployment-version-manifest', brainId, candidate?.id],
    queryFn: () => fetchDeploymentVersionManifest(brainId, candidate!.id), enabled: Boolean(candidate) })
  const sources = useQuery({ queryKey: ['compatible-deployment-source-artifacts', brainId, candidate?.id],
    queryFn: () => fetchCompatibleDeploymentSourceArtifacts(brainId, candidate!.id), enabled: Boolean(candidate && canOperate) })
  const destinations = useQuery({ queryKey: ['brain-target-options', brainId, auth.session?.role],
    queryFn: () => customer ? fetchDeploymentTargetOptions(brainId) : fetchDeploymentTargetProfiles() })
  const productionDestinations = (destinations.data ?? []).filter(item => item.active && item.environmentName === 'production')
  const sourceRequired = customer || candidate?.sourceCapabilityManifestRequired === true
  const selectedSource = sources.data?.find(item => item.id === sourceArtifactId) ?? null
  const targetReleases = (releases.data ?? []).filter(item => item.targetProfileId === targetProfileId).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const appliedEvidenceMissing = !appliedRelease && targetReleases.some(item => ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(item.status))
  const latestRelease = targetReleases[0] ?? null
  const inProgress = releases.data?.find(updatesReleaseInProgress)
  const environment = environmentLabel(targetProfile?.environmentName ?? 'selected environment')
  const targetReady = Boolean(targetProfile?.active && targetProfile.id === targetProfileId)
  const changes = buildUpdateChanges(draft.data, appliedManifest.data, appliedVersionId, diff.data, catalogue.data)
  const changed = changes.filter(item => item.changed !== false)
  const detail = changes.find(item => item.key === detailKey) ?? null
  const baseline: PreviewBaseline = appliedEvidenceMissing || !releases.isSuccess ? 'unavailable' : !appliedVersionId ? 'new'
    : record(appliedManifest.data) && appliedManifest.data.deploymentId === brainId && appliedManifest.data.versionId === appliedVersionId ? 'applied' : 'unavailable'
  const currentConfig = baseline === 'applied' ? appliedManifest.data : undefined
  const detailBaseline = detail ? sectionPreviewBaseline(currentConfig, detail.key, baseline) : baseline
  const detailPreview = detail && buildChangePreview(detail.key, record(currentConfig) ? currentConfig[detail.key] : undefined,
    draft.data?.[detail.key as keyof DeploymentDraftResponse], detail.changed === null ? 'unavailable' : detailBaseline, catalogue.data)
  const currentLabel = baseline === 'new' ? `No version applied to ${environment}` : baseline === 'unavailable' ? `${environment} unavailable`
    : `${environment} ${versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId}`
  const proposedLabel = `Saved draft r${draft.data?.revisionNumber ?? '—'}`
  const comparisonReady = !appliedEvidenceMissing && changes.length > 0 && changes.every(item => item.changed !== null) && releases.isSuccess
    && (!appliedVersionId || appliedManifest.isSuccess)
  const exactManifest = (value: unknown, versionId: string) => record(value) && value.deploymentId === brainId && value.versionId === versionId
  const candidateReady = Boolean(candidate && candidateManifest.isSuccess && exactManifest(candidateManifest.data, candidate.id))
  const canPublish = canEdit && draft.isSuccess && versions.isSuccess && releases.isSuccess && !inProgress
  const canApply = canOperate && candidateReady && targetReady && releases.isSuccess && !inProgress
    && !appliedEvidenceMissing && (!appliedVersionId || (appliedManifest.isSuccess && exactManifest(appliedManifest.data, appliedVersionId)))
    && sources.isSuccess && (!sourceArtifactId || Boolean(selectedSource)) && (!sourceRequired || Boolean(selectedSource))

  const refresh = useCallback(async () => {
    await Promise.all(['deployment-draft', 'deployment-config-diff-center', 'deployment-versions', 'deployment-releases',
      'deployment-workspace', 'deployment-verification-runs', 'brain-updates-target', 'deployments'].map(key =>
      client.invalidateQueries({ queryKey: key === 'deployments' ? [key] : [key, brainId] })))
  }, [brainId, client])
  const wasReleasing = useRef(false)
  useEffect(() => {
    if (wasReleasing.current && !inProgress) void refresh()
    wasReleasing.current = Boolean(inProgress)
  }, [inProgress?.id, refresh])
  const validate = useMutation({ mutationFn: () => validateDeploymentDraft(draft.data!.id) })
  const validatedStamp = useRef('')
  const validationCurrent = validatedStamp.current === draftStamp(draft.data) && validate.isSuccess

  const publish = useMutation({
    mutationFn: async () => {
      if (!review || !canPublish || !review.confirmed) throw new Error('Review and confirm the saved draft first.')
      const current = await fetchDeploymentDraft(brainId)
      if (draftStamp(current) !== review.draftStamp) throw new Error('The shared draft changed. Refresh and review the latest changes before publishing.')
      const version = await publishDeploymentDraft(current.id)
      // Retain the immutable result before any subsequent read or apply can fail.
      publishedConfiguration.current = stableConfig(sections.map(([key]) => current[key]))
      setPublished(version)
      setSelectedVersion(version)
      setSourceArtifactId('')
      setNotice(`${version.versionLabel} published. The selected target has not been updated.`)
      setReview(review.mode === 'publish-only' ? null : { mode: 'apply', draftStamp: '', confirmed: false, versionId: version.id, appliedReleaseId: appliedRelease?.id ?? null })
      await refresh()
      return version
    },
    onError: failure => setError(`${errorMessage(failure)} Refresh version history before repeating a publication whose result is uncertain.`),
  })
  const apply = useMutation({
    mutationFn: async () => {
      if (!canApply || !candidate || !review?.confirmed || review.versionId !== candidate.id) throw new Error('Confirm the exact target, version and runtime source before updating.')
      const currentReleases = await fetchDeploymentReleases(brainId)
      if (currentReleases.some(updatesReleaseInProgress)) throw new Error('A release is already in progress. Refresh its status before updating.')
      const currentApplied = currentReleases.filter(item => item.targetProfileId === targetProfileId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .find(item => ['APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(item.status) && item.provisioningStatus === 'ACTIVE')
      if ((currentApplied?.id ?? null) !== review.appliedReleaseId) throw new Error('The applied target changed during review. Refresh and review the new target state.')
      const identity = `${brainId}:${targetProfileId}:${candidate.id}:${sourceArtifactId}`
      if (operation.current?.identity !== identity) operation.current = { identity, key: crypto.randomUUID() }
      const result = await applyDeploymentVersion(brainId, candidate.id, targetProfileId, sourceArtifactId || undefined, operation.current.key)
      operation.current = null
      client.setQueryData<DeploymentReleaseSummary[]>(['deployment-releases', brainId], old => [result, ...(old ?? []).filter(item => item.id !== result.id)])
      setReview(null)
      setNotice(`Release ${result.id}: ${result.currentStepDescription ?? result.status}. Verification follows the existing release workflow.`)
      await refresh()
      return result
    },
    onError: failure => setError(`${errorMessage(failure)} ${candidate?.versionLabel ?? 'The version'} remains published. Retry this version without publishing again.`),
  })
  const verify = useMutation({ mutationFn: (releaseId: string) => rerunDeploymentVerification(brainId, releaseId),
    onSuccess: async result => { setNotice(result.summaryMessage); await refresh() }, onError: failure => setError(errorMessage(failure)) })
  const reconcile = useMutation({ mutationFn: () => reconcileDeploymentRelease(brainId),
    onSuccess: async result => { setNotice(result.currentStepDescription ?? result.status); await refresh() }, onError: failure => setError(errorMessage(failure)) })
  const busy = publish.isPending || apply.isPending || verify.isPending || reconcile.isPending

  const openReview = (mode: Review['mode'], versionId = candidate?.id) => {
    setError(null)
    setReview({ mode, draftStamp: draftStamp(draft.data), confirmed: false, versionId, appliedReleaseId: appliedRelease?.id ?? null })
  }
  const continueApply = Boolean(continuation && continuation.id !== appliedVersionId && canOperate)
  const primaryLabel = continueApply ? `Review & apply ${continuation!.versionLabel}` : 'Review & publish version'
  const editor = {
    dirty: false, busy,
    canSave: continueApply ? canOperate && targetReady && !inProgress : canPublish && !publishedMatchesDraft,
    save: !review && !versionDetail && !detail && (canEdit || canOperate) ? () => { setSelectedVersion(null); openReview(continueApply ? 'apply' : 'publish-update', continuation?.id) } : undefined,
    discard: () => navigate(href('responses')), primaryLabel, secondaryLabel: 'Back to editing',
    message: inProgress ? `Release in progress: ${inProgress.currentStepDescription ?? inProgress.status}`
      : latestRelease?.status === 'APPLIED_VERIFIED' && published?.id === latestRelease.deploymentVersionId
        ? `${published.versionLabel} applied; verification ${latestRelease.verificationStatus}.`
      : published ? `${published.versionLabel} published; apply is a separate recorded step.`
        : comparisonReady && changed.length === 0 ? 'Saved shared draft matches the applied target.'
          : `Shared draft r${draft.data?.revisionNumber ?? '—'} saved · ${environment} unchanged.`, error,
  }
  const customerTask = customer && ['connections', 'assignment', 'promotion'].includes(view)
  useBrainEditor(customerTask ? null : ['history', 'versions', 'technical'].includes(view) ? { dirty: false, busy, message: `${environment} · ${view === 'versions' ? 'Published versions' : view === 'technical' ? 'Technical version tools' : 'Update activity'}` } : editor)

  const filteredHistory = targetReleases.filter(item => !search || [item.id, item.deploymentVersionId, item.status, item.currentStepDescription ?? ''].some(value => value.toLowerCase().includes(search.toLowerCase())))
  const visibleHistory = filteredHistory.slice(page * 6, page * 6 + 6)
  const queryError = [draft, versions, releases].find(query => query.isError)?.error
  const candidateSnapshot = record(candidateManifest.data) ? candidateManifest.data : null
  const applyRows = candidate && candidateSnapshot ? sections.map(([key, label]) => ({ key, label,
    changed: !appliedVersionId ? true : !record(appliedManifest.data) || !Object.prototype.hasOwnProperty.call(appliedManifest.data, key) ? null
      : stableConfig(appliedManifest.data[key]) !== stableConfig(candidateSnapshot[key]),
    current: appliedVersionId ? (key === 'promptConfig' && record(appliedManifest.data) ? responseSummary(appliedManifest.data[key], catalogue.data) : null)
      ?? summary(record(appliedManifest.data) ? appliedManifest.data[key] : undefined) : 'Not applied',
    next: (key === 'promptConfig' ? responseSummary(candidateSnapshot[key], catalogue.data) : null) ?? summary(candidateSnapshot[key]),
  })) : []
  const submitReview = async () => {
    if (operationRunning.current) return
    operationRunning.current = true
    setError(null)
    try {
      if (review?.mode === 'apply') await apply.mutateAsync()
      else await publish.mutateAsync()
    } catch { /* The mutation keeps the exact error and published version visible. */ }
    finally { operationRunning.current = false }
  }
  const reviewVersion = (version: DeploymentVersionSummary, sourceId = '') => {
    setSelectedVersion(version); setSourceArtifactId(sourceId); setVersionDetail(false)
    openReview('apply', version.id)
  }
  const versionGrid = (all = false) => <BrainReleaseVersions versions={versions.data ?? []} profiles={destinations.data ?? []}
    releases={releases.data ?? []} evidenceReady={releases.isSuccess && destinations.isSuccess} selectedTargetId={targetProfileId}
    selectedVersionId={candidate?.id} all={all} busy={busy || Boolean(inProgress)} canOperate={canOperate}
    onReview={reviewVersion} onDetails={version => { setSelectedVersion(version); setVersionDetail(true); setError(null) }}
    onActivity={() => changeView('history')} />
  const currentReleaseState = targetReleaseState(releases.data ?? [], targetProfileId)
  const activeStep = review?.mode === 'publish-update' || review?.mode === 'publish-only' ? 1
    : inProgress?.targetProfileId === targetProfileId ? inProgress.status === 'VERIFYING' || inProgress.verificationStatus === 'RUNNING' ? 3 : 2
      : continuation && !currentReleaseState.failed && currentReleaseState.active?.deploymentVersionId === continuation.id
        && currentReleaseState.active.status === 'APPLIED_VERIFIED' && currentReleaseState.active.verificationStatus === 'PASSED' ? 4
        : continuation ? 2 : 0
  const outcomeRows = changed.length ? changed : changes
  const stageVerified = (destinations.data ?? []).filter(item => item.environmentName === 'staging')
    .some(item => targetReleaseState(releases.data ?? [], item.id).label === 'Verified')
  const productionExists = productionDestinations.some(item => (releases.data ?? []).some(release => release.targetProfileId === item.id))

  return <Box data-brain-updates sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, minWidth: 0,
    '& td, & th': { overflowWrap: 'anywhere' } }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1} sx={{ flexShrink: 0, mb: 2 }}>
      <Stack direction="row" alignItems="center" gap={1}>
        {view !== 'review' && <Tooltip title="Back to update review"><IconButton aria-label="Back to update review" disabled={busy} onClick={() => changeView('review')}><ArrowBackRounded /></IconButton></Tooltip>}
        <Typography component="h1" sx={{ fontSize: 26, fontWeight: 650, lineHeight: 1.3 }}>{customerTask ? view === 'promotion' ? 'Promote tested staging' : view === 'assignment' ? 'Connect Engage app' : 'Required connections' : view === 'history' ? 'Update activity' : view === 'technical' ? 'Technical version tools' : 'Updates'}</Typography>
      </Stack>
      <Stack direction="row" gap={0.5}>
        {view !== 'history' && <Button startIcon={<HistoryRounded />} disabled={busy} onClick={() => changeView('history')}>Activity</Button>}
        {view !== 'technical' && <Tooltip title="Technical version tools"><IconButton aria-label="Technical version tools" disabled={busy} onClick={() => changeView('technical')}><SettingsOutlined /></IconButton></Tooltip>}
        <Tooltip title="Refresh saved draft and release status"><IconButton aria-label="Refresh updates" disabled={busy} onClick={() => void refresh()}><RefreshRounded /></IconButton></Tooltip>
      </Stack>
    </Stack>
    {view === 'review' && <Stepper activeStep={activeStep} aria-label="Update sequence" sx={{ flexShrink: 0, pb: 2, mb: 2, borderBottom: 1, borderColor: 'divider', '& .MuiStepLabel-label': { fontSize: { xs: 10, sm: 13 } }, '& .MuiStep-root': { px: { xs: 0.25, sm: 1 } } }}>
      {['Review changes', 'Publish version', `Apply to ${environment}`, 'Verify'].map(label => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
    </Stepper>}
    {busy && <LinearProgress aria-label="Update operation in progress" />}
    <Box sx={{ minHeight: 0, overflow: 'auto', flex: 1, display: 'flex', flexDirection: 'column' }}>
      <Stack spacing={1.5} sx={{ minHeight: 0, flex: 1 }}>
        {queryError && <Alert severity="error">{errorMessage(queryError)}</Alert>}
        {panelUnavailable && <Alert severity="warning">The requested release task belongs to a different environment. Select the matching environment above; no release operation was started.</Alert>}
        {!targetReady && destinations.isSuccess && <Alert severity="info">Select an authorized active target in the workspace before updating. The shared draft can still be published as a version.</Alert>}
        {appliedEvidenceMissing && <Alert severity="warning">An applied release exists, but the workspace could not resolve its target evidence. Refresh before publishing an update.</Alert>}
        {access && !canEdit && !canOperate && <Alert severity="info">Read-only access. Publishing requires editor access; updating and verification require operator access.</Alert>}
        {notice && <Alert severity="info" onClose={() => setNotice('')}>{notice}</Alert>}
        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
        {latestRelease && latestRelease.status !== 'APPLIED_VERIFIED' && <Alert severity={statusColor(latestRelease.status) === 'error' ? 'error' : latestRelease.status === 'APPLIED_VERIFICATION_FAILED' ? 'warning' : 'info'}>
          <Stack spacing={0.5}><Typography variant="body2" fontWeight={600}>Latest update: {latestRelease.status.split('_').join(' ')}</Typography>
            <Typography variant="body2">{latestRelease.currentStepDescription ?? 'No step detail recorded.'}</Typography>
            {latestRelease.errorMessage && <Typography variant="body2">{latestRelease.errorMessage}</Typography>}
            <Typography variant="caption">Release {latestRelease.id} / Verification: {latestRelease.verificationStatus}</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {['FAILED', 'PRE_APPLY_BLOCKED'].includes(latestRelease.status) && canOperate && <Button size="small" startIcon={<RefreshRounded />} disabled={busy || Boolean(inProgress)} onClick={() => {
                const version = versions.data?.find(item => item.id === latestRelease.deploymentVersionId)
                if (version) reviewVersion(version, latestRelease.sourceArtifactId ?? '')
                else setError('The published version is unavailable. Refresh version history before retrying.')
              }}>Retry published version</Button>}
              {canOperate && ['APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(latestRelease.status) && <Button size="small" startIcon={<CheckCircleOutlineRounded />} disabled={busy || Boolean(inProgress)} onClick={() => {
                if (window.confirm(`Run verification for ${environment} (${targetProfileId}), release ${latestRelease.id}?`)) verify.mutate(latestRelease.id)
              }}>Run verification again</Button>}
              {canOperate && latestRelease.provisioningStatus === 'AWAITING_CONFIRMATION' && releases.data?.[0]?.id === latestRelease.id && <Button size="small" startIcon={<RefreshRounded />} disabled={busy} onClick={() => reconcile.mutate()}>Reconcile activation</Button>}
            </Stack>
          </Stack>
        </Alert>}
        {customerTask ? !targetReady || (view === 'promotion' ? !production : !staging)
          ? <Alert severity="warning">Choose the matching environment in the Smart Brain header to continue this release task.</Alert>
          : <BrainCustomerReleaseTasks session={auth.session!} section={view as 'connections' | 'assignment' | 'promotion'} canEdit={canEdit} canOperate={canOperate} />
        : view === 'technical' ? <Box sx={{ overflow: 'auto', minHeight: 0 }}><RevisionsPage scope={{ brainId, targetProfileId }} /></Box>
        : view === 'versions' ? versionGrid(true)
        : view === 'review' ? <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.3fr) minmax(0, 1fr)' }, gap: 3, flex: 1, minHeight: 0 }}>
          <Stack spacing={1.5} sx={{ minWidth: 0, minHeight: 0 }}>
            <Box><Typography component="h2" fontSize={19} fontWeight={650}>What will change</Typography>
              <Typography variant="body2" color="text.secondary">{appliedEvidenceMissing ? 'Applied state unavailable' : appliedRelease ? `Compared with ${environment} ${versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId}` : releases.isSuccess ? `New setup for ${environment}` : 'Loading applied state...'}</Typography></Box>
            {draft.isLoading || appliedManifest.isLoading ? <LinearProgress aria-label="Loading saved changes" /> : null}
            {appliedManifest.isError && <Alert severity="warning">Applied-version comparison unavailable: {errorMessage(appliedManifest.error)}</Alert>}
            <Box sx={{ flex: 1, minHeight: { xs: 100, lg: 80 }, overflow: 'auto', pr: 0.5 }}>
              <OutcomeChanges changes={outcomeRows} config={draft.data} currentConfig={currentConfig} catalogue={catalogue.data} baseline={baseline} onSelect={setDetail} />
              {comparisonReady && changed.length === 0 && <Typography variant="body2" color="text.secondary">The saved draft matches the applied configuration.</Typography>}
            </Box>
            <Typography variant="caption" color="text.secondary"><InfoOutlined sx={{ fontSize: 14, verticalAlign: 'middle', mr: 0.5 }} />Configuration preview · AI responses may vary</Typography>
            <Button sx={{ alignSelf: 'flex-start' }} size="small" startIcon={<InfoOutlined />} onClick={() => setDetail(outcomeRows[0]?.key ?? null)} disabled={!changes.length}>View change details</Button>
            <Stack spacing={1} sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
              <Typography component="h2" fontSize={17} fontWeight={650}>Before publishing</Typography>
              <Stack direction="row" gap={1} alignItems="center"><InfoOutlined fontSize="small" color={validationCurrent && validate.data.publishReady ? 'success' : 'action'} />
                <Typography variant="body2" sx={{ flex: 1 }}>{validationCurrent ? `${validate.data.publishReady ? 'Ready to publish' : 'Needs attention'} · ${validate.data.warningCount} warnings` : `Not checked for draft r${draft.data?.revisionNumber ?? '—'}`}</Typography>
                {canEdit && <Button size="small" variant="outlined" disabled={!draft.data || busy || validate.isPending} onClick={() => { validatedStamp.current = draftStamp(draft.data); validate.mutate() }}>Check draft</Button>}
              </Stack>
              {validate.isError && <Alert severity="error">{errorMessage(validate.error)}</Alert>}
              {validationCurrent && validate.data.issues.length > 0 && <Box sx={{ maxHeight: 70, overflow: 'auto' }}>{validate.data.issues.map((item, index) => <Typography key={index} variant="caption" display="block">{item.severity}: {item.message}</Typography>)}</Box>}
              <Typography variant="caption" color="text.secondary">Publishing saves the shared draft. {environment} stays unchanged until you apply a version.</Typography>
            </Stack>
          </Stack>
          <Stack spacing={1.25} sx={{ minWidth: 0, minHeight: 0, overflow: 'auto', borderLeft: { lg: 1 }, borderColor: { lg: 'divider' }, pl: { lg: 3 } }}>
            {versions.isLoading && <LinearProgress aria-label="Loading published versions" />}
            {versions.isSuccess && versionGrid()}
            <Button size="small" sx={{ alignSelf: 'flex-start' }} disabled={busy} onClick={() => changeView('versions')}>All versions</Button>
            {destinations.isError && <Alert severity="warning">Environment states unavailable: {errorMessage(destinations.error)}</Alert>}
            <Stack spacing={1} sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
              <Typography component="h2" fontSize={17} fontWeight={650}>{environment}</Typography>
              <Typography variant="body2">{releases.isError || appliedEvidenceMissing ? 'Applied version unavailable' : releases.isPending ? 'Loading applied version...' : appliedVersionId ? `Applied ${versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId}` : 'No version applied'}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{targetContext.data?.runtimeBaseUrl ?? (targetContext.isError ? 'Runtime address unavailable' : targetReady ? 'No runtime address confirmed' : 'Choose an environment')}</Typography>
              <Stack direction="row" gap={1} flexWrap="wrap" useFlexGap>
                {customer && staging && <Button size="small" disabled={busy} onClick={() => changeView('connections')}>Required connections</Button>}
                {customer && production && (stageVerified || targetReleases.length > 0) && <Button size="small" disabled={busy} onClick={() => changeView('promotion')}>{stageVerified ? 'Promote tested staging' : 'Production workspace'}</Button>}
                {latestRelease?.status === 'APPLIED_VERIFIED' && latestRelease.verificationStatus === 'PASSED' && <>
                  <Button size="small" component={RouterLink} to={`${href('try')}&releaseId=${encodeURIComponent(latestRelease.id)}`}>Try your AI</Button>
                  {customer && staging && <Button size="small" onClick={() => changeView('assignment')}>Connect Engage app</Button>}
                </>}
                {customer && staging && (stageVerified || productionExists) && productionDestinations.length > 0 && <Button size="small" component={RouterLink}
                  to={productionDestinations.length === 1 ? `${brainPath(brainId, 'updates', productionDestinations[0].id)}&panel=promotion`
                    : `/deployments?${new URLSearchParams({ view: 'setup', deploymentId: brainId, step: 'production' })}`} disabled={busy}>{stageVerified ? 'Review go-live' : 'Production workspace'}</Button>}
                {canOperate && appliedRelease?.status === 'APPLIED_VERIFIED' && <Button size="small" startIcon={<CheckCircleOutlineRounded />} disabled={busy || Boolean(inProgress)} onClick={() => {
                  if (window.confirm(`Run verification for ${environment} (${targetProfileId}), release ${appliedRelease.id}?`)) verify.mutate(appliedRelease.id)
                }}>Verify again</Button>}
              </Stack>
            </Stack>
          </Stack>
        </Box> : <Stack spacing={1.5} sx={{ minHeight: 0, overflow: 'auto' }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <Typography component="h2" sx={{ fontSize: 19, fontWeight: 650, flex: 1 }}>{environment} releases</Typography>
            <TextField size="small" label="Search activity" value={search} onChange={event => { setSearch(event.target.value); setPage(0) }} />
          </Stack>
          <TableContainer><Table size="small" aria-label="Selected target release history"><TableHead><TableRow>
            <TableCell>Version / release</TableCell><TableCell>Update</TableCell><TableCell>Verification</TableCell><TableCell>Updated</TableCell>
          </TableRow></TableHead><TableBody>{visibleHistory.map(item => <TableRow key={item.id}>
            <TableCell><Typography variant="body2" fontWeight={600}>{versions.data?.find(version => version.id === item.deploymentVersionId)?.versionLabel ?? item.deploymentVersionId}</Typography><Typography variant="caption">{item.id}</Typography>
              {canOperate && <Button size="small" disabled={busy || Boolean(inProgress) || !versions.data?.some(version => version.id === item.deploymentVersionId)} onClick={() => {
                const version = versions.data?.find(version => version.id === item.deploymentVersionId)
                if (version) reviewVersion(version, item.sourceArtifactId ?? '')
              }}>Review update</Button>}
            </TableCell>
            <TableCell><Chip size="small" color={statusColor(item.status)} label={item.status.split('_').join(' ')} /><Typography variant="caption" display="block">{item.currentStepDescription}</Typography>{item.errorMessage && <Typography variant="caption" color="error.main">{item.errorMessage}</Typography>}</TableCell>
            <TableCell><Chip size="small" variant="outlined" color={statusColor(item.verificationStatus)} label={item.verificationStatus} /><Typography variant="caption" display="block">{verification.data?.find(run => run.id === item.verificationRunId)?.summaryMessage ?? item.verificationRunId ?? 'No run recorded'}</Typography></TableCell>
            <TableCell>{formatTime(item.updatedAt)}</TableCell>
          </TableRow>)}</TableBody></Table></TableContainer>
          {releases.isSuccess && !filteredHistory.length && <Alert severity="info">No releases match this target and search.</Alert>}
          {verification.isError && <Alert severity="warning">Verification details unavailable: {errorMessage(verification.error)}</Alert>}
          <TablePagination component="div" count={filteredHistory.length} page={page} rowsPerPage={6} rowsPerPageOptions={[6]} onPageChange={(_, value) => setPage(value)} />
        </Stack>}
      </Stack>
    </Box>

    <Drawer anchor="right" open={Boolean(review || detail || versionDetail)} onClose={() => { if (!busy) { setReview(null); setDetail(null); setVersionDetail(false) } }}
      PaperProps={{ role: 'dialog', 'aria-labelledby': 'updates-review-title', sx: { width: { xs: '100%', sm: detail ? 720 : 580 }, maxWidth: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} sx={{ px: 3, py: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Box>{detail && <Typography variant="overline" color="text.secondary" sx={{ fontSize: 10 }}>Saved change · {detail.label}</Typography>}
          <Typography component="h2" id="updates-review-title" fontSize={22} fontWeight={650}>{detailPreview ? detailPreview.title : versionDetail ? `Version ${candidate?.versionLabel ?? ''}` : review?.mode === 'apply' ? `Update ${environment}` : 'Publish a version'}</Typography></Box>
        <IconButton aria-label="Close update details" disabled={busy} onClick={() => { setReview(null); setDetail(null); setVersionDetail(false) }}><CloseRounded /></IconButton>
      </Stack>
      <Stack spacing={2} sx={{ px: 3, py: 2, flex: 1, minHeight: 0, overflow: 'auto' }}>
        {error && <Alert severity="error">{error}</Alert>}
        {detailPreview ? <BrainChangeComparison preview={detailPreview} currentLabel={currentLabel} proposedLabel={proposedLabel} href={href} canEdit={canEdit} /> : <>
          <Typography fontSize={15} fontWeight={600}>{workspace?.deployment.name ?? brainId}</Typography>
          {review?.mode === 'apply' || versionDetail ? <>
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '140px minmax(0, 1fr)', gap: 1, fontSize: 14, '& dd': { m: 0, overflowWrap: 'anywhere' } }}>
              <Box component="dt" color="text.secondary">Environment</Box><Box component="dd">{environment}<Typography variant="caption" color="text.secondary" display="block">{targetProfile?.name ?? 'Not selected'}</Typography></Box>
              <Box component="dt" color="text.secondary">Current version</Box><Box component="dd">{appliedVersionId ? versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId : releases.isSuccess ? 'Not deployed' : 'Unavailable'}</Box>
              <Box component="dt" color="text.secondary">Selected version</Box><Box component="dd">{candidate?.versionLabel ?? 'Unavailable'} · {candidate?.status === 'PUBLISHED' ? 'Published' : candidate?.status ?? 'Status unavailable'}</Box>
            </Box>
            <Divider />
            {!candidateReady && <Alert severity={candidateManifest.isError ? 'error' : 'info'}>{candidateManifest.isError ? errorMessage(candidateManifest.error) : 'Loading the published version...'}</Alert>}
            {candidateReady && <>
              <Typography component="h3" fontSize={17} fontWeight={650}>What will change</Typography>
              <OutcomeChanges changes={applyRows.filter(item => item.changed !== false)} config={candidateManifest.data} currentConfig={currentConfig} catalogue={catalogue.data} baseline={baseline} />
              {applyRows.every(item => item.changed === false) && <Typography variant="body2" color="text.secondary">Configuration matches the applied version.</Typography>}
            </>}
            {review?.mode === 'apply' && <>
              <TextField select size="small" fullWidth label="Runtime package" InputLabelProps={{ shrink: true }} value={selectedSource?.id ?? ''} SelectProps={{ renderValue: () => selectedSource?.imageTag ?? (sourceRequired ? 'Select a compatible runtime package' : 'Use promoted channel default'), displayEmpty: true,
                SelectDisplayProps: { 'aria-label': 'Runtime package', 'aria-labelledby': undefined } }}
                onChange={event => { setSourceArtifactId(event.target.value); setReview(current => current && { ...current, confirmed: false }) }} disabled={busy || sources.isLoading}>
                <MenuItem value="">{sourceRequired ? 'Select a compatible runtime package' : 'Use promoted channel default'}</MenuItem>
                {(sources.data ?? []).map(item => <MenuItem key={item.id} value={item.id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}><Box><Typography variant="body2">{item.imageTag}</Typography><Typography variant="caption" color="text.secondary">{item.id}</Typography></Box></MenuItem>)}
              </TextField>
              {sources.isError && <Alert severity="error">{errorMessage(sources.error)}</Alert>}
              {sources.isSuccess && sourceRequired && !sources.data.length && <Alert severity="warning">No compatible runtime image is available for this published version. The version is retained; no target update has been requested.</Alert>}
            </>}
            <Accordion disableGutters elevation={0} sx={{ bgcolor: 'transparent', '&:before': { display: 'none' }, borderTop: 1, borderColor: 'divider' }}>
              <AccordionSummary expandIcon={<ExpandMoreRounded />}><Typography variant="body2">Technical details</Typography></AccordionSummary>
              <AccordionDetails><Stack spacing={1} sx={{ overflowWrap: 'anywhere' }}>
                <Typography variant="caption">Brain: {brainId}<br />Target: {targetProfileId || 'Not selected'}<br />Version: {candidate?.id}<br />Published: {formatTime(candidate?.publishedAt)}<br />Indexing required: {candidate?.reindexRequired ? 'Yes' : 'No'}<br />Configuration hash: {candidate?.configHash}</Typography>
                {selectedSource && <Typography variant="caption">Runtime: {selectedSource.imageReference}</Typography>}
                <Typography variant="caption" color="text.secondary">Published runtime configuration compared with this environment. Compilation may add settings.</Typography>
                <ChangeTable changes={applyRows} nextLabel="Selected version" />
                <Button startIcon={<SettingsOutlined />} onClick={() => { setReview(null); setVersionDetail(false); changeView('technical') }}>Open technical version tools</Button>
              </Stack></AccordionDetails>
            </Accordion>
            {review?.mode === 'apply' && <Stack spacing={1} sx={{ borderTop: 1, borderColor: 'divider', pt: 2 }}>
              <Typography component="h3" fontSize={17} fontWeight={650}>After applying</Typography>
              <Typography variant="body2">1. Update {environment}</Typography><Typography variant="body2">2. Verify the release</Typography>
              <Typography variant="caption" color="text.secondary">Other environments and app assignments remain unchanged.</Typography>
            </Stack>}
          </> : <>
            <Typography variant="body2">Saved shared draft r{draft.data?.revisionNumber} · {formatTime(draft.data?.updatedAt)}</Typography>
            <OutcomeChanges changes={outcomeRows} config={draft.data} currentConfig={currentConfig} catalogue={catalogue.data} baseline={baseline} />
            <Alert severity="info">Publishing saves a version. It does not update Staging or Production.</Alert>
          </>}
        </>}
      </Stack>
      <Box sx={{ px: 3, py: 2, borderTop: 1, borderColor: 'divider', flexShrink: 0 }}>
        {review && <FormControlLabel sx={{ mb: 1, alignItems: 'center' }} control={<Checkbox checked={review.confirmed} disabled={busy} onChange={(_, checked) => setReview(current => current && { ...current, confirmed: checked })} />}
          label={<Typography variant="body2">{review.mode === 'apply' ? `Confirm ${candidate?.versionLabel ?? 'this version'} for ${environment}` : `Confirm publication of draft r${draft.data?.revisionNumber}`}</Typography>} />}
        <Stack direction="row" justifyContent="flex-end" gap={1} alignItems="center">
          {detail && <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>Preview only · No live changes</Typography>}
          <Button disabled={busy} onClick={() => { setReview(null); setDetail(null); setVersionDetail(false) }}>{detail ? 'Back to changes' : review ? 'Cancel' : 'Close'}</Button>
          {review && <Button variant="contained" startIcon={review.mode === 'apply' ? <RocketLaunchRounded /> : <PublishRounded />} disabled={busy || !review.confirmed || (review.mode === 'apply' ? !canApply : !canPublish)} onClick={() => void submitReview()}>
            {busy ? 'Working...' : review.mode === 'apply' ? `Apply ${candidate?.versionLabel ?? ''} to ${environment}` : 'Publish version'}
          </Button>}
          {versionDetail && candidate && canOperate && candidate.id !== appliedVersionId && <Button variant="contained" disabled={busy || Boolean(inProgress) || !targetReady} onClick={() => reviewVersion(candidate)}>Review & apply</Button>}
        </Stack>
      </Box>
    </Drawer>
  </Box>
}
