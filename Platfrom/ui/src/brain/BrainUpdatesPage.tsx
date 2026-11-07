import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import HistoryRounded from '@mui/icons-material/HistoryRounded'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import PublishRounded from '@mui/icons-material/PublishRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import RocketLaunchRounded from '@mui/icons-material/RocketLaunchRounded'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, Drawer, FormControlLabel, IconButton, LinearProgress, MenuItem, Stack,
  Tab, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, Tabs, TextField, Tooltip, Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  applyDeploymentVersion, fetchCompatibleDeploymentSourceArtifacts, fetchDeploymentAiBehaviourProfiles, fetchDeploymentConfigDiffCenter,
  fetchDeploymentDraft, fetchDeploymentReleases, fetchDeploymentTargetContext,
  fetchDeploymentVerificationRuns, fetchDeploymentVersionManifest, fetchDeploymentVersions,
  publishDeploymentDraft, reconcileDeploymentRelease, rerunDeploymentVerification, validateDeploymentDraft,
  type DeploymentConfigDiffCenterSummary, type DeploymentDraftResponse, type DeploymentReleaseSummary,
  type DeploymentVersionSummary, type AiBehaviourCatalogue,
} from '../api/platformApi'
import { promptConfigIdentity as stableConfig, readAiBehaviour } from '../aiBehaviourProfiles'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { RevisionsPage } from '../pages/RevisionsPage'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'

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

export function draftStamp(draft: DeploymentDraftResponse | undefined): string {
  return draft ? `${draft.id}:${draft.updatedAt}:${stableConfig(sections.map(([key]) => draft[key]))}` : ''
}

export function updatesReleaseInProgress(release: DeploymentReleaseSummary): boolean {
  return ['APPLY_REQUESTED', 'PRE_APPLY_VERIFYING', 'PROVISIONING', 'VERIFYING'].includes(release.status)
    || ['QUEUED', 'RUNNING', 'AWAITING_CONFIRMATION'].includes(release.provisioningStatus)
    || release.verificationStatus === 'RUNNING'
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
  const client = useQueryClient()
  const [view, setView] = useState<'review' | 'history'>('review')
  const [review, setReview] = useState<Review | null>(null)
  const [published, setPublished] = useState<DeploymentVersionSummary | null>(null)
  const [sourceArtifactId, setSourceArtifactId] = useState('')
  const [detail, setDetail] = useState<Change | null>(null)
  const [advanced, setAdvanced] = useState(false)
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
  const candidate = published ?? (publishedMatchesDraft ? versions.data?.find(item => item.id === workspace?.lifecycle.latestPublishedVersionId) ?? null : null)
  const candidateManifest = useQuery({ queryKey: ['deployment-version-manifest', brainId, candidate?.id],
    queryFn: () => fetchDeploymentVersionManifest(brainId, candidate!.id), enabled: Boolean(candidate) })
  const sources = useQuery({ queryKey: ['compatible-deployment-source-artifacts', brainId, candidate?.id],
    queryFn: () => fetchCompatibleDeploymentSourceArtifacts(brainId, candidate!.id), enabled: Boolean(candidate && canOperate) })
  const sourceRequired = customer || candidate?.sourceCapabilityManifestRequired === true
  const selectedSource = sources.data?.find(item => item.id === sourceArtifactId) ?? null
  const targetReleases = (releases.data ?? []).filter(item => item.targetProfileId === targetProfileId).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const appliedEvidenceMissing = !appliedRelease && targetReleases.some(item => ['VERIFYING', 'APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(item.status))
  const latestRelease = targetReleases[0] ?? null
  const inProgress = releases.data?.find(updatesReleaseInProgress)
  const environment = targetProfile?.environmentName ?? 'selected environment'
  const targetReady = Boolean(targetProfile?.active && targetProfile.id === targetProfileId)
  const changes = buildUpdateChanges(draft.data, appliedManifest.data, appliedVersionId, diff.data, catalogue.data)
  const changed = changes.filter(item => item.changed !== false)
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
      setPublished(version)
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
  const primaryLabel = candidate ? `Update ${environment} to ${candidate.versionLabel}` : `Publish & update ${environment}`
  const editor = {
    dirty: false, busy,
    canSave: candidate ? canOperate && targetReady && !inProgress && candidate.id !== appliedVersionId
      : canPublish && canOperate && targetReady && comparisonReady && changed.length > 0,
    save: canEdit || canOperate ? () => openReview(candidate ? 'apply' : 'publish-update') : undefined,
    discard: () => navigate(href('responses')), primaryLabel, secondaryLabel: 'Back to editing',
    message: inProgress ? `Release in progress: ${inProgress.currentStepDescription ?? inProgress.status}`
      : latestRelease?.status === 'APPLIED_VERIFIED' && published?.id === latestRelease.deploymentVersionId
        ? `${published.versionLabel} applied; verification ${latestRelease.verificationStatus}.`
      : published ? `${published.versionLabel} published; apply is a separate recorded step.`
        : comparisonReady && changed.length === 0 ? 'Saved shared draft matches the applied target.'
          : 'Saved shared draft; applied configuration is unchanged.', error,
  }
  useBrainEditor(editor)

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

  return <Box data-brain-updates sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0, minWidth: 0,
    '& td, & th': { overflowWrap: 'anywhere' } }}>
    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1} sx={{ flexShrink: 0 }}>
      <Typography component="h1" sx={{ fontSize: 26, fontWeight: 650, lineHeight: 1.3 }}>Review and publish changes</Typography>
      <Tooltip title="Refresh saved draft and release status"><IconButton aria-label="Refresh updates" disabled={busy} onClick={() => void refresh()}><RefreshRounded /></IconButton></Tooltip>
    </Stack>
    <Tabs value={view} onChange={(_, value) => setView(value)} aria-label="Updates views" sx={{ borderBottom: 1, borderColor: 'divider', mb: 2, flexShrink: 0 }}>
      <Tab value="review" label="Review changes" /><Tab value="history" label="History" icon={<HistoryRounded sx={{ fontSize: 18 }} />} iconPosition="start" />
    </Tabs>
    {busy && <LinearProgress aria-label="Update operation in progress" />}
    <Box sx={{ minHeight: 0, overflow: 'auto', flex: 1 }}>
      <Stack spacing={1.5}>
        {queryError && <Alert severity="error">{errorMessage(queryError)}</Alert>}
        {!targetReady && <Alert severity="info">Select an authorized active target in the workspace before updating. The shared draft can still be published as a version.</Alert>}
        {appliedEvidenceMissing && <Alert severity="warning">An applied release exists, but the workspace could not resolve its target evidence. Refresh before publishing an update.</Alert>}
        {!canEdit && !canOperate && <Alert severity="info">Read-only access. Publishing requires editor access; updating and verification require operator access.</Alert>}
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
                if (version) { setPublished(version); setSourceArtifactId(latestRelease.sourceArtifactId ?? ''); openReview('apply', version.id) }
                else setError('The published version is unavailable. Refresh version history before retrying.')
              }}>Retry published version</Button>}
              {canOperate && ['APPLIED_VERIFIED', 'APPLIED_VERIFICATION_FAILED'].includes(latestRelease.status) && <Button size="small" startIcon={<CheckCircleOutlineRounded />} disabled={busy || Boolean(inProgress)} onClick={() => {
                if (window.confirm(`Run verification for ${environment} (${targetProfileId}), release ${latestRelease.id}?`)) verify.mutate(latestRelease.id)
              }}>Run verification again</Button>}
              {canOperate && latestRelease.provisioningStatus === 'AWAITING_CONFIRMATION' && releases.data?.[0]?.id === latestRelease.id && <Button size="small" startIcon={<RefreshRounded />} disabled={busy} onClick={() => reconcile.mutate()}>Reconcile activation</Button>}
            </Stack>
          </Stack>
        </Alert>}
        {view === 'review' ? <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.35fr) minmax(280px, 1fr)' }, gap: 3 }}>
          <Stack spacing={2} sx={{ minWidth: 0 }}>
            <Box><Typography component="h2" sx={{ fontSize: 19, fontWeight: 650 }}>Changes in this draft</Typography>
              <Typography variant="body2" color="text.secondary">{appliedRelease ? `Compared with ${environment} ${versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId}` : `No applied release recorded for ${environment}`}</Typography>
            </Box>
            {draft.isLoading || appliedManifest.isLoading ? <LinearProgress aria-label="Loading saved changes" /> : null}
            {appliedManifest.isError && <Alert severity="warning">Applied-version comparison unavailable: {errorMessage(appliedManifest.error)}</Alert>}
            <ChangeTable changes={changed.length ? changed : changes} maxHeight={290} />
            {comparisonReady && changed.length === 0 && <Typography variant="body2" color="text.secondary">The saved draft matches the applied configuration.</Typography>}
            <Button sx={{ alignSelf: 'flex-start' }} startIcon={<InfoOutlined />} onClick={() => setDetail(changes[0] ?? null)} disabled={!changes.length}>View change details</Button>
            <Divider />
            <Typography component="h2" sx={{ fontSize: 19, fontWeight: 650 }}>Before updating</Typography>
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} alignItems="center"><InfoOutlined fontSize="small" color={validationCurrent && validate.data.publishReady ? 'success' : 'action'} />
                <Typography variant="body2" sx={{ flex: 1 }}>{validationCurrent ? `Configuration: ${validate.data.publishReady ? 'ready to publish' : 'blocking findings'} (${validate.data.warningCount} warnings)` : 'Configuration has not been checked for this saved draft'}</Typography>
                {canEdit && <Button size="small" disabled={!draft.data || busy || validate.isPending} onClick={() => { validatedStamp.current = draftStamp(draft.data); validate.mutate() }}>Check</Button>}
              </Stack>
              {validate.isError && <Alert severity="error">{errorMessage(validate.error)}</Alert>}
              {validationCurrent && validate.data.issues.length > 0 && <Box sx={{ maxHeight: 110, overflow: 'auto' }}>{validate.data.issues.map((item, index) => <Typography key={index} variant="caption" display="block">{item.severity}: {item.message}</Typography>)}</Box>}
              <Typography variant="body2">Access: {canEdit ? 'publish allowed' : 'publish not allowed'}; {canOperate ? 'update allowed' : 'update not allowed'}.</Typography>
              <Typography variant="body2" color="text.secondary">Connection and runtime checks are recorded by the existing apply and verification workflow, not assumed from a saved draft.</Typography>
            </Stack>
          </Stack>
          <Stack spacing={2} sx={{ minWidth: 0, borderLeft: { lg: 1 }, borderColor: { lg: 'divider' }, pl: { lg: 3 } }}>
            <Typography component="h2" sx={{ fontSize: 19, fontWeight: 650 }}>What will happen</Typography>
            {['Publish an immutable saved version', `Update the existing ${environment} target`, 'Verify the applied release'].map((label, index) => <Stack key={label} direction="row" spacing={1.5} alignItems="center">
              <Box sx={{ width: 28, height: 28, flexShrink: 0, borderRadius: '50%', border: 1, borderColor: 'divider', display: 'grid', placeItems: 'center', fontSize: 13 }}>{index + 1}</Box><Typography variant="body2">{label}</Typography>
            </Stack>)}
            <Divider />
            <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: '85px minmax(0, 1fr)', gap: 1, fontSize: 14, '& dd': { m: 0, overflowWrap: 'anywhere' } }}>
              <Box component="dt" fontWeight={600}>Target</Box><Box component="dd">{environment}<Typography variant="caption" display="block" color="text.secondary">{targetProfile?.name} / {targetProfileId || 'Not selected'}</Typography></Box>
              <Box component="dt" fontWeight={600}>Applied</Box><Box component="dd">{appliedVersionId ? versions.data?.find(item => item.id === appliedVersionId)?.versionLabel ?? appliedVersionId : 'Not deployed'}</Box>
              <Box component="dt" fontWeight={600}>Runtime</Box><Box component="dd">{targetContext.data?.runtimeBaseUrl ?? (targetContext.isError ? 'Status unavailable' : 'No runtime address confirmed')}</Box>
              <Box component="dt" fontWeight={600}>Verification</Box><Box component="dd">{appliedRelease?.verificationStatus ?? 'Not recorded'}</Box>
            </Box>
            {candidate && <Alert severity="info">{candidate.versionLabel} is already published. Updating it does not publish the current shared draft again.</Alert>}
            <Typography variant="body2" color="text.secondary">Existing Brain and connected app identities are retained. Only the explicitly selected target is requested; no automatic rollback runs.</Typography>
            <Button startIcon={<SettingsOutlined />} sx={{ alignSelf: 'flex-start' }} onClick={() => setAdvanced(true)}>Advanced version actions</Button>
            {canOperate && appliedRelease?.status === 'APPLIED_VERIFIED' && <Button startIcon={<CheckCircleOutlineRounded />} sx={{ alignSelf: 'flex-start' }} disabled={busy || Boolean(inProgress)} onClick={() => {
              if (window.confirm(`Run verification for ${environment} (${targetProfileId}), release ${appliedRelease.id}?`)) verify.mutate(appliedRelease.id)
            }}>Run verification again</Button>}
            {canEdit && <Button startIcon={<PublishRounded />} sx={{ alignSelf: 'flex-start' }} disabled={!canPublish || busy} onClick={() => openReview('publish-only')}>Publish version only</Button>}
          </Stack>
        </Box> : <Stack spacing={1.5}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <Typography component="h2" sx={{ fontSize: 19, fontWeight: 650, flex: 1 }}>{environment} update history</Typography>
            <TextField size="small" label="Search history" value={search} onChange={event => { setSearch(event.target.value); setPage(0) }} />
            <Button startIcon={<SettingsOutlined />} onClick={() => setAdvanced(true)}>Version actions</Button>
          </Stack>
          <TableContainer><Table size="small" aria-label="Selected target release history"><TableHead><TableRow>
            <TableCell>Version / release</TableCell><TableCell>Update</TableCell><TableCell>Verification</TableCell><TableCell>Updated</TableCell>
          </TableRow></TableHead><TableBody>{visibleHistory.map(item => <TableRow key={item.id}>
            <TableCell><Typography variant="body2" fontWeight={600}>{versions.data?.find(version => version.id === item.deploymentVersionId)?.versionLabel ?? item.deploymentVersionId}</Typography><Typography variant="caption">{item.id}</Typography>
              {canOperate && <Button size="small" disabled={busy || Boolean(inProgress) || !versions.data?.some(version => version.id === item.deploymentVersionId)} onClick={() => {
                const version = versions.data?.find(version => version.id === item.deploymentVersionId)
                if (version) { setPublished(version); setSourceArtifactId(item.sourceArtifactId ?? ''); openReview('apply', version.id) }
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

    <Dialog open={Boolean(review)} onClose={() => { if (!busy) setReview(null) }} fullWidth maxWidth="md" aria-labelledby="updates-review-title">
      <DialogTitle id="updates-review-title">{review?.mode === 'apply' ? `Update ${environment}` : review?.mode === 'publish-only' ? 'Publish a version only' : `Publish & update ${environment}`}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <Typography variant="body2">{workspace?.deployment.name ?? brainId} / {brainId}</Typography>
          {review?.mode !== 'publish-only' && <Alert severity="warning">Exact target: {environment} / {targetProfile?.name} / {targetProfileId}. {review?.mode === 'apply' ? `Version: ${candidate?.versionLabel ?? 'Unavailable'} / ${candidate?.id ?? ''}.` : 'Publishing saves a version first. Confirm its compatible runtime image before the target is updated.'}</Alert>}
          {review?.mode === 'apply' ? <>
            {!candidateReady && <Alert severity={candidateManifest.isError ? 'error' : 'info'}>{candidateManifest.isError ? errorMessage(candidateManifest.error) : 'Loading the immutable published version...'}</Alert>}
            <Typography variant="subtitle2">Proposed published-version changes</Typography>
            <Typography variant="caption" color="text.secondary">Published runtime artifact compared with the selected target. Compilation can add runtime-only settings.</Typography>
            <ChangeTable changes={applyRows} nextLabel="Proposed version" maxHeight={240} />
            <TextField select size="small" fullWidth label="Runtime source artifact" value={selectedSource?.id ?? ''} onChange={event => { setSourceArtifactId(event.target.value); setReview(current => current && { ...current, confirmed: false }) }} disabled={busy || sources.isLoading}>
              <MenuItem value="">{sourceRequired ? 'Select a compatible runtime image' : 'Use promoted channel default'}</MenuItem>
              {(sources.data ?? []).map(item => <MenuItem key={item.id} value={item.id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{item.imageTag} / {item.id}</MenuItem>)}
            </TextField>
            {selectedSource && <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>{selectedSource.imageReference}</Typography>}
            {sources.isError && <Alert severity="error">{errorMessage(sources.error)}</Alert>}
            {sources.isSuccess && sourceRequired && !sources.data.length && <Alert severity="warning">No compatible runtime image is available for this published version. The version is retained; no target update has been requested.</Alert>}
          </> : <>
            <Typography variant="body2">Saved shared draft r{draft.data?.revisionNumber}, saved {formatTime(draft.data?.updatedAt)}.</Typography>
            <ChangeTable changes={changes} maxHeight={280} />
            <Typography variant="body2">{review?.mode === 'publish-only' ? 'This creates immutable artifacts and the next shared draft. It does not update any runtime.' : 'The published version is retained even if updating or verification fails. No replacement app or automatic rollback is requested.'}</Typography>
          </>}
          <FormControlLabel control={<Checkbox checked={review?.confirmed ?? false} disabled={busy} onChange={(_, checked) => setReview(current => current && { ...current, confirmed: checked })} />} label={review?.mode === 'publish-only' ? 'I confirm publication of this saved draft without updating a target.' : `I confirm these proposed changes for ${environment} (${targetProfileId}).`} />
        </Stack>
      </DialogContent>
      <DialogActions><Button startIcon={<CloseRounded />} disabled={busy} onClick={() => setReview(null)}>Cancel</Button>
        <Button variant="contained" startIcon={review?.mode === 'apply' ? <RocketLaunchRounded /> : <PublishRounded />} disabled={busy || !review?.confirmed || (review.mode === 'apply' ? !canApply : !canPublish || (review.mode === 'publish-update' && (!comparisonReady || !targetReady || !canOperate)))} onClick={() => void submitReview()}>
          {busy ? 'Working...' : review?.mode === 'apply' ? `Update ${environment}` : review?.mode === 'publish-only' ? 'Publish version' : 'Publish & continue'}
        </Button>
      </DialogActions>
    </Dialog>

    <Drawer anchor="right" open={Boolean(detail) || advanced} onClose={() => { setDetail(null); setAdvanced(false) }} PaperProps={{ sx: { width: { xs: '100%', md: advanced ? 'min(1100px, 92vw)' : 680 }, maxWidth: '100%', p: 2.5 } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ mb: 2 }}><Typography component="h2" sx={{ fontSize: 20, fontWeight: 650 }}>{advanced ? 'Advanced version actions' : 'Change details'}</Typography>
        <IconButton aria-label="Close update details" onClick={() => { setDetail(null); setAdvanced(false) }}><CloseRounded /></IconButton></Stack>
      {advanced ? <RevisionsPage scope={{ brainId, targetProfileId }} /> : detail && <Stack spacing={2} sx={{ minHeight: 0 }}>
        <TextField select size="small" label="Configuration area" value={detail.key} onChange={event => setDetail(changes.find(item => item.key === event.target.value) ?? null)}>{changes.map(item => <MenuItem key={item.key} value={item.key}>{item.label}</MenuItem>)}</TextField>
        <Typography variant="body2" color="text.secondary">Field names only. Stored values and credentials are not projected here. Runtime artifact compilation may add settings absent from the shared draft.</Typography>
        <TableContainer sx={{ overflow: 'auto' }}><Table size="small"><TableHead><TableRow><TableCell>Field</TableCell><TableCell>Change</TableCell></TableRow></TableHead><TableBody>
          {configurationFieldChanges(record(appliedManifest.data) ? appliedManifest.data[detail.key] : undefined, draft.data?.[detail.key as keyof DeploymentDraftResponse]).map(item => <TableRow key={item.path}><TableCell sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>{item.path}</TableCell><TableCell>{item.change}</TableCell></TableRow>)}
        </TableBody></Table></TableContainer>
        <Button startIcon={<ArrowBackRounded />} onClick={() => setDetail(null)} sx={{ alignSelf: 'flex-start' }}>Back to changes</Button>
      </Stack>}
    </Drawer>
  </Box>
}

export function configurationFieldChanges(current: unknown, next: unknown, path = '$'): Array<{ path: string; change: string }> {
  if (stableConfig(current) === stableConfig(next)) return []
  if (record(current) && record(next)) return [...new Set([...Object.keys(current), ...Object.keys(next)])].sort()
    .flatMap(key => configurationFieldChanges(current[key], next[key], `${path}.${key}`))
  return [{ path, change: current === undefined ? 'Added' : next === undefined ? 'Removed' : 'Changed' }]
}
