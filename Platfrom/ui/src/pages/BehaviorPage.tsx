import AccountTreeRoundedIcon from '@mui/icons-material/AccountTreeRounded'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import BlockRoundedIcon from '@mui/icons-material/BlockRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import VerifiedUserRoundedIcon from '@mui/icons-material/VerifiedUserRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  approveDeploymentBehaviorReadiness,
  fetchDeploymentBehaviors,
  fetchDeploymentBehaviorReadiness,
  fetchDeploymentDraft,
  fetchDeploymentExecutionExtensions,
  updateDeploymentDraft,
  withdrawDeploymentBehaviorReadiness,
} from '../api/platformApi'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type BehaviorSelections = {
  activationSources: string[]
  channelBindings: string[]
  executionExtensions: string[]
  smartBrain: SmartBrainSettings | null
}

type SmartBrainTrigger = {
  code: string
  name: string
  eventTypes: string
  enabled: boolean
}

type SmartBrainSchedule = {
  code: string
  triggerCode: string
  cron: string
  zoneId: string
  enabled: boolean
}

type SmartBrainSettings = {
  maxEventBytes: number
  triggers: SmartBrainTrigger[]
  schedules: SmartBrainSchedule[]
  deliveryMode: 'POLL' | 'SIGNED_WEBHOOK'
  callbackUrl: string
}

const EMPTY_SELECTIONS: BehaviorSelections = {
  activationSources: [],
  channelBindings: [],
  executionExtensions: [],
  smartBrain: null,
}

const READINESS_APPROVAL_AREAS = [
  ['BEHAVIOR', 'Behavior'],
  ['SECURITY_ISOLATION', 'Security and isolation'],
  ['LIFECYCLE_RECOVERY', 'Lifecycle and recovery'],
  ['OPERATIONS', 'Operations'],
  ['COST_LIMITS', 'Cost and limits'],
  ['CUSTOMER_UX', 'Customer experience'],
  ['SUPPORT', 'Support'],
  ['COMMERCIAL', 'Commercial'],
  ['CONTROLLED_PRODUCTION', 'Controlled production'],
] as const

type ReadinessApprovalArea = typeof READINESS_APPROVAL_AREAS[number][0]
type ReadinessApprovalEvidenceDraft = Record<ReadinessApprovalArea, { evidenceRef: string; summary: string }>

function emptyApprovalEvidence(): ReadinessApprovalEvidenceDraft {
  return Object.fromEntries(
    READINESS_APPROVAL_AREAS.map(([area]) => [area, { evidenceRef: '', summary: '' }]),
  ) as ReadinessApprovalEvidenceDraft
}

function defaultApprovalExpiry(): string {
  const value = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
  return new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === 'string')
    : []
}

function readSelections(config: unknown): BehaviorSelections {
  const root = isRecord(config) ? config : {}
  const activation = isRecord(root.activation) ? root.activation : {}
  return {
    activationSources: stringArray(activation.sources),
    channelBindings: stringArray(root.channelBindings),
    executionExtensions: stringArray(root.executionExtensions),
    smartBrain: readSmartBrain(root.smartBrain),
  }
}

function readSmartBrain(value: unknown): SmartBrainSettings | null {
  if (!isRecord(value)) return null
  const delivery = isRecord(value.delivery) ? value.delivery : {}
  const triggers = Array.isArray(value.triggers) ? value.triggers : []
  const schedules = Array.isArray(value.schedules) ? value.schedules : []
  return {
    maxEventBytes: typeof value.maxEventBytes === 'number' ? value.maxEventBytes : 262144,
    triggers: triggers.filter(isRecord).map((trigger) => ({
      code: typeof trigger.code === 'string' ? trigger.code : '',
      name: typeof trigger.name === 'string' ? trigger.name : '',
      eventTypes: stringArray(trigger.eventTypes).join(', '),
      enabled: typeof trigger.enabled !== 'boolean' || trigger.enabled,
    })),
    schedules: schedules.filter(isRecord).map((schedule) => ({
      code: typeof schedule.code === 'string' ? schedule.code : '',
      triggerCode: typeof schedule.triggerCode === 'string' ? schedule.triggerCode : '',
      cron: typeof schedule.cron === 'string' ? schedule.cron : '',
      zoneId: typeof schedule.zoneId === 'string' ? schedule.zoneId : 'UTC',
      enabled: typeof schedule.enabled !== 'boolean' || schedule.enabled,
    })),
    deliveryMode: delivery.mode === 'SIGNED_WEBHOOK' ? 'SIGNED_WEBHOOK' : 'POLL',
    callbackUrl: typeof delivery.callbackUrl === 'string' ? delivery.callbackUrl : '',
  }
}

function selectionsEqual(left: BehaviorSelections, right: BehaviorSelections): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

function toggleValue(values: string[], value: string): string[] {
  return values.includes(value)
    ? values.filter((entry) => entry !== value)
    : [...values, value]
}

function buildBehaviorConfig(config: unknown, selections: BehaviorSelections): unknown {
  if (!isRecord(config)) {
    throw new Error('The saved deployment behavior contract is unavailable.')
  }
  const next = JSON.parse(JSON.stringify(config)) as Record<string, unknown>
  const activation = isRecord(next.activation) ? next.activation : {}
  activation.sources = selections.activationSources
  next.activation = activation
  next.channelBindings = selections.channelBindings
  next.executionExtensions = selections.executionExtensions
  if (selections.smartBrain) {
    const currentSmartBrain = isRecord(next.smartBrain) ? next.smartBrain : {}
    next.smartBrain = {
      ...currentSmartBrain,
      contractVersion: 'LOOMAI_SMART_BRAIN_CONFIG_V1',
      maxEventBytes: selections.smartBrain.maxEventBytes,
      triggers: selections.smartBrain.triggers.map((trigger) => ({
        code: trigger.code.trim(),
        name: trigger.name.trim(),
        eventTypes: trigger.eventTypes.split(',').map((value) => value.trim()).filter(Boolean),
        specialistRef: 'smart-brain-event-analyst@1',
        enabled: trigger.enabled,
      })),
      schedules: selections.smartBrain.schedules.map((schedule) => ({
        code: schedule.code.trim(),
        triggerCode: schedule.triggerCode.trim(),
        cron: schedule.cron.trim(),
        zoneId: schedule.zoneId.trim(),
        enabled: schedule.enabled,
      })),
      delivery: {
        mode: selections.smartBrain.deliveryMode,
        callbackUrl: selections.smartBrain.deliveryMode === 'SIGNED_WEBHOOK'
          ? selections.smartBrain.callbackUrl.trim()
          : null,
      },
    }
  }
  return next
}

function readableCode(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function readSpecialistBundles(config: unknown): Array<Record<string, unknown>> {
  if (!isRecord(config) || !Array.isArray(config.specialistBundles)) return []
  return config.specialistBundles.filter(isRecord)
}

function shortHash(value: string | null | undefined): string {
  if (!value) return 'Not exposed'
  return value.length > 22 ? `${value.slice(0, 14)}…${value.slice(-7)}` : value
}

function readinessColor(maturity: string): 'success' | 'warning' | 'info' | 'default' {
  if (maturity === 'MARKET_READY') return 'success'
  if (maturity === 'HOSTED_PROVEN') return 'info'
  if (maturity === 'PLATFORM_SELECTABLE') return 'warning'
  return 'default'
}

export function BehaviorPage() {
  const { selectedDeploymentId, selectedDeploymentSummary, workspace } = useDeploymentWorkspace()
  const auth = usePlatformAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [selections, setSelections] = useState<BehaviorSelections>(EMPTY_SELECTIONS)
  const [approvalCandidateId, setApprovalCandidateId] = useState('')
  const [approvalEvidence, setApprovalEvidence] = useState<ReadinessApprovalEvidenceDraft>(emptyApprovalEvidence)
  const [approvalNote, setApprovalNote] = useState('')
  const [approvalExpiresAt, setApprovalExpiresAt] = useState(defaultApprovalExpiry)
  const [withdrawCandidateId, setWithdrawCandidateId] = useState('')
  const [withdrawReason, setWithdrawReason] = useState('')

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })
  const behaviorQuery = useQuery({
    queryKey: ['deployment-behaviors'],
    queryFn: fetchDeploymentBehaviors,
  })
  const extensionQuery = useQuery({
    queryKey: ['deployment-execution-extensions'],
    queryFn: fetchDeploymentExecutionExtensions,
  })
  const readinessQuery = useQuery({
    queryKey: ['deployment-behavior-readiness', selectedDeploymentSummary?.behaviorType],
    queryFn: () => fetchDeploymentBehaviorReadiness(selectedDeploymentSummary?.behaviorType),
    enabled: Boolean(selectedDeploymentSummary?.behaviorType),
    retry: false,
  })

  useEffect(() => {
    setSelections(draftQuery.data ? readSelections(draftQuery.data.behaviorConfig) : EMPTY_SELECTIONS)
  }, [draftQuery.data])

  const behavior = useMemo(
    () => (behaviorQuery.data ?? []).find((item) => item.code === selectedDeploymentSummary?.behaviorType) ?? null,
    [behaviorQuery.data, selectedDeploymentSummary?.behaviorType],
  )
  const compatibleExtensions = useMemo(
    () => (extensionQuery.data ?? []).filter((extension) => (
      extension.compatibleBehaviorTypes.includes(selectedDeploymentSummary?.behaviorType ?? '')
    )),
    [extensionQuery.data, selectedDeploymentSummary?.behaviorType],
  )
  const savedSelections = useMemo(
    () => readSelections(draftQuery.data?.behaviorConfig),
    [draftQuery.data?.behaviorConfig],
  )
  const selectedSpecialistBundles = useMemo(
    () => readSpecialistBundles(draftQuery.data?.behaviorConfig),
    [draftQuery.data?.behaviorConfig],
  )
  const dirty = draftQuery.data ? !selectionsEqual(selections, savedSelections) : false
  const canEdit = workspace?.access.canEdit ?? false
  const canManageReadiness = auth.session != null
    && (auth.session.enabled ? auth.session.role === 'PLATFORM_ADMIN' : true)
  const approvalReady = approvalNote.trim().length > 0
    && approvalExpiresAt.length > 0
    && READINESS_APPROVAL_AREAS.every(([area]) => (
      approvalEvidence[area].evidenceRef.trim().length > 0
      && approvalEvidence[area].summary.trim().length > 0
    ))
  const selectionError = selections.activationSources.length === 0
    ? 'Select at least one activation source.'
    : selections.channelBindings.length === 0
      ? 'Select at least one channel binding.'
      : smartBrainError(selections.smartBrain, selectedDeploymentSummary?.behaviorType)

  useDeploymentWorkspaceEditorState(selectedDeploymentId ? {
    dirty,
    label: 'Behavior contract',
    description: dirty
      ? 'Behavior choices have unsaved browser-only changes.'
      : 'Behavior choices match the saved deployment draft.',
  } : null)

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!draftQuery.data) {
        throw new Error('Deployment draft is unavailable.')
      }
      return updateDeploymentDraft(draftQuery.data.id, {
        behaviorConfig: buildBehaviorConfig(draftQuery.data.behaviorConfig, selections),
      })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
      ])
    },
  })

  const approveReadinessMutation = useMutation({
    mutationFn: () => approveDeploymentBehaviorReadiness(approvalCandidateId, {
      evidence: READINESS_APPROVAL_AREAS.map(([area]) => ({
        area,
        status: 'PASSED' as const,
        evidenceRef: approvalEvidence[area].evidenceRef.trim(),
        summary: approvalEvidence[area].summary.trim(),
      })),
      approvalNote: approvalNote.trim(),
      expiresAt: new Date(approvalExpiresAt).toISOString(),
    }),
    onSuccess: async () => {
      setApprovalCandidateId('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-behavior-readiness'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-behaviors'] }),
      ])
    },
  })

  const withdrawReadinessMutation = useMutation({
    mutationFn: () => withdrawDeploymentBehaviorReadiness(withdrawCandidateId, withdrawReason.trim()),
    onSuccess: async () => {
      setWithdrawCandidateId('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-behavior-readiness'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-behaviors'] }),
      ])
    },
  })

  const openApproval = (candidateId: string) => {
    setApprovalCandidateId(candidateId)
    setApprovalEvidence(emptyApprovalEvidence())
    setApprovalNote('')
    setApprovalExpiresAt(defaultApprovalExpiry())
    approveReadinessMutation.reset()
  }

  const openWithdrawal = (candidateId: string) => {
    setWithdrawCandidateId(candidateId)
    setWithdrawReason('')
    withdrawReadinessMutation.reset()
  }

  if (!selectedDeploymentId) {
    return <Alert severity="info">Select a deployment to configure its behavior contract.</Alert>
  }

  if (draftQuery.isLoading || behaviorQuery.isLoading || extensionQuery.isLoading) {
    return <Alert severity="info">Loading deployment behavior contract…</Alert>
  }

  if (draftQuery.isError || behaviorQuery.isError || extensionQuery.isError || !behavior) {
    const error = draftQuery.error ?? behaviorQuery.error ?? extensionQuery.error
    return (
      <Alert severity="error">
        {error instanceof Error ? error.message : 'The deployment behavior contract could not be loaded.'}
      </Alert>
    )
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Stack spacing={0.75}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <AccountTreeRoundedIcon color="primary" />
            <Typography variant="h4">{behavior.name}</Typography>
            <Chip label={behavior.maturity} color={behavior.maturity === 'HOSTED_PROVEN' ? 'success' : 'warning'} />
          </Stack>
          <Typography color="text.secondary" sx={{ maxWidth: 900 }}>
            {behavior.description}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Behavior type is immutable for this deployment. Create another deployment to choose a different behavior.
          </Typography>
        </Stack>
        <Button
          variant="contained"
          startIcon={<SaveRoundedIcon />}
          disabled={!canEdit || !dirty || Boolean(selectionError) || saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
          sx={{ alignSelf: { xs: 'stretch', md: 'flex-start' } }}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save behavior'}
        </Button>
      </Stack>

      <Alert severity={behavior.releaseRequiresCapabilityManifest ? 'warning' : 'success'}>
        {behavior.availabilityMessage}
      </Alert>

      <Box component="section">
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
          <Box>
            <Typography variant="h6">Exact release readiness</Typography>
            <Typography variant="body2" color="text.secondary">
              Maturity belongs to an immutable Marketplace template, V04 composition, and runtime image, not to every deployment of this behavior.
            </Typography>
          </Box>
          <Chip
            label={`Highest maturity: ${behavior.maturity.replace(/_/g, ' ')}`}
            color={readinessColor(behavior.maturity)}
            variant="outlined"
          />
        </Stack>
        {readinessQuery.isLoading ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>Loading release evidence…</Typography>
        ) : readinessQuery.isError ? (
          <Alert severity="info" sx={{ mt: 1.5 }}>
            Detailed release evidence is available to Platform operators. The maturity label above remains evidence-backed.
          </Alert>
        ) : (readinessQuery.data ?? []).length === 0 ? (
          <Alert severity="warning" sx={{ mt: 1.5 }}>
            No current exact hosted-release evidence is recorded for this behavior. Authoring remains available, but this is not a market-ready claim.
          </Alert>
        ) : (
          <Stack divider={<Divider flexItem />} sx={{ mt: 1.5 }}>
            {(readinessQuery.data ?? []).map((candidate) => (
              <Stack key={candidate.id} spacing={1.25} sx={{ py: 1.5 }}>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    {candidate.templatePluginId}@{candidate.templatePluginVersion}
                  </Typography>
                  <Chip
                    size="small"
                    label={candidate.effectiveMaturity.replace(/_/g, ' ')}
                    color={readinessColor(candidate.effectiveMaturity)}
                  />
                  {candidate.expired ? <Chip size="small" label="Evidence expired" color="error" variant="outlined" /> : null}
                  {candidate.status !== 'ACTIVE' ? <Chip size="small" label={candidate.status} color="error" variant="outlined" /> : null}
                </Stack>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={`AI Fabric ${candidate.frameworkVersion}`} variant="outlined" />
                  <Chip size="small" label={`Composition ${shortHash(candidate.compositionHash)}`} variant="outlined" />
                  <Chip size="small" label={`Image ${shortHash(candidate.imageDigest)}`} variant="outlined" />
                  {candidate.hostedProofs.map((proof) => (
                    <Chip
                      key={`${candidate.id}-${proof.environment}-${proof.verifiedAt}`}
                      size="small"
                      label={`${proof.environment}: ${proof.verificationStatus} until ${new Date(proof.expiresAt).toLocaleDateString()}`}
                      color={proof.verificationStatus === 'PASSED' ? 'success' : 'warning'}
                      variant="outlined"
                    />
                  ))}
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Verification packs: {candidate.verificationPackIds.join(', ')} · evidence expires {new Date(candidate.expiresAt).toLocaleString()}
                </Typography>
                {canManageReadiness && candidate.status === 'ACTIVE' ? (
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<VerifiedUserRoundedIcon />}
                      disabled={
                        !['HOSTED_PROVEN', 'MARKET_READY'].includes(candidate.effectiveMaturity)
                        || !['staging', 'production'].every((environment) => candidate.hostedProofs.some((proof) => (
                          proof.environment === environment
                          && proof.releaseStatus === 'APPLIED_VERIFIED'
                          && proof.verificationStatus === 'PASSED'
                          && new Date(proof.expiresAt).getTime() > Date.now()
                        )))
                      }
                      onClick={() => openApproval(candidate.id)}
                    >
                      {candidate.effectiveMaturity === 'MARKET_READY' ? 'Renew market decision' : 'Review for market ready'}
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      variant="text"
                      startIcon={<BlockRoundedIcon />}
                      onClick={() => openWithdrawal(candidate.id)}
                    >
                      Withdraw evidence
                    </Button>
                  </Stack>
                ) : null}
              </Stack>
            ))}
          </Stack>
        )}
      </Box>

      <Dialog
        open={approvalCandidateId.length > 0}
        onClose={() => !approveReadinessMutation.isPending && setApprovalCandidateId('')}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Market-ready evidence decision</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <Alert severity="warning">
              This promotes only the exact immutable candidate. Every area requires a real reviewed evidence reference; no default approval is supplied.
            </Alert>
            {READINESS_APPROVAL_AREAS.map(([area, label]) => (
              <Box component="section" key={area}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>{label}</Typography>
                <Grid container spacing={1.5}>
                  <Grid item xs={12} md={5}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Evidence reference"
                      value={approvalEvidence[area].evidenceRef}
                      onChange={(event) => setApprovalEvidence((current) => ({
                        ...current,
                        [area]: { ...current[area], evidenceRef: event.target.value },
                      }))}
                    />
                  </Grid>
                  <Grid item xs={12} md={7}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Customer-safe reviewed outcome"
                      value={approvalEvidence[area].summary}
                      onChange={(event) => setApprovalEvidence((current) => ({
                        ...current,
                        [area]: { ...current[area], summary: event.target.value },
                      }))}
                    />
                  </Grid>
                </Grid>
              </Box>
            ))}
            <Divider />
            <TextField
              fullWidth
              multiline
              minRows={2}
              label="Approval decision note"
              value={approvalNote}
              onChange={(event) => setApprovalNote(event.target.value)}
            />
            <TextField
              type="datetime-local"
              label="Decision expires"
              value={approvalExpiresAt}
              onChange={(event) => setApprovalExpiresAt(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            {approveReadinessMutation.isError ? (
              <Alert severity="error">
                {approveReadinessMutation.error instanceof Error
                  ? approveReadinessMutation.error.message
                  : 'Market-ready approval failed.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={approveReadinessMutation.isPending} onClick={() => setApprovalCandidateId('')}>
            Cancel
          </Button>
          <Button
            variant="contained"
            startIcon={<VerifiedUserRoundedIcon />}
            disabled={!approvalReady || approveReadinessMutation.isPending}
            onClick={() => approveReadinessMutation.mutate()}
          >
            Approve exact candidate
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={withdrawCandidateId.length > 0}
        onClose={() => !withdrawReadinessMutation.isPending && setWithdrawCandidateId('')}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Withdraw readiness evidence</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2}>
            <Alert severity="warning">
              Withdrawal immediately returns this exact candidate to platform-selectable maturity and cannot be undone for the same immutable material.
            </Alert>
            <TextField
              autoFocus
              fullWidth
              multiline
              minRows={3}
              label="Withdrawal reason"
              value={withdrawReason}
              onChange={(event) => setWithdrawReason(event.target.value)}
            />
            {withdrawReadinessMutation.isError ? (
              <Alert severity="error">
                {withdrawReadinessMutation.error instanceof Error
                  ? withdrawReadinessMutation.error.message
                  : 'Readiness withdrawal failed.'}
              </Alert>
            ) : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={withdrawReadinessMutation.isPending} onClick={() => setWithdrawCandidateId('')}>
            Cancel
          </Button>
          <Button
            color="error"
            variant="contained"
            startIcon={<BlockRoundedIcon />}
            disabled={withdrawReason.trim().length === 0 || withdrawReadinessMutation.isPending}
            onClick={() => withdrawReadinessMutation.mutate()}
          >
            Withdraw exact candidate
          </Button>
        </DialogActions>
      </Dialog>

      {selectionError ? <Alert severity="error">{selectionError}</Alert> : null}
      {saveMutation.isError ? (
        <Alert severity="error">
          {saveMutation.error instanceof Error ? saveMutation.error.message : 'Behavior save failed.'}
        </Alert>
      ) : null}
      {saveMutation.isSuccess ? <Alert severity="success">Behavior draft saved. Publish a new revision before apply.</Alert> : null}

      <Box component="section">
        <Typography variant="h6">Activation</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Choose which trusted request classes may start this deployment behavior.
        </Typography>
        <Grid container spacing={1}>
          {behavior.activationSources.map((source) => (
            <Grid item xs={12} md={4} key={source}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={selections.activationSources.includes(source)}
                    onChange={() => setSelections((current) => ({
                      ...current,
                      activationSources: toggleValue(current.activationSources, source),
                    }))}
                    disabled={!canEdit}
                  />
                )}
                label={readableCode(source)}
              />
            </Grid>
          ))}
        </Grid>
      </Box>

      <Divider />

      {selectedDeploymentSummary?.behaviorType === 'SMART_BRAIN' && selections.smartBrain ? (
        <SmartBrainConfiguration
          value={selections.smartBrain}
          disabled={!canEdit}
          onChange={(smartBrain) => setSelections((current) => ({ ...current, smartBrain }))}
        />
      ) : null}

      {selectedDeploymentSummary?.behaviorType === 'SMART_BRAIN' ? <Divider /> : null}

      <Box component="section">
        <Typography variant="h6">Channels</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Select only the product surfaces this deployment will expose.
        </Typography>
        <Grid container spacing={1}>
          {behavior.channelBindings.map((channel) => (
            <Grid item xs={12} md={4} key={channel}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={selections.channelBindings.includes(channel)}
                    onChange={() => setSelections((current) => ({
                      ...current,
                      channelBindings: toggleValue(current.channelBindings, channel),
                    }))}
                    disabled={!canEdit}
                  />
                )}
                label={readableCode(channel)}
              />
            </Grid>
          ))}
        </Grid>
      </Box>

      <Divider />

      <Box component="section">
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5}>
          <Box>
            <Typography variant="h6">Specialist Bundles</Typography>
            <Typography variant="body2" color="text.secondary">
              Exact reviewed bundles are installed through Marketplace and remain bound to their plugin version and source hash.
            </Typography>
          </Box>
          {behavior.requiredSpecialistBundles.length > 0 ? (
            <Button
              variant="outlined"
              startIcon={<StorefrontRoundedIcon />}
              onClick={() => navigate(`/marketplace?targetDeploymentId=${encodeURIComponent(selectedDeploymentId)}&category=specialist`)}
              sx={{ alignSelf: { xs: 'stretch', md: 'flex-start' } }}
            >
              Open Specialists
            </Button>
          ) : null}
        </Stack>
        {behavior.requiredSpecialistBundles.length === 0 ? (
          <Alert severity="info" sx={{ mt: 1.5 }}>This behavior does not require a specialist bundle.</Alert>
        ) : (
          <Stack spacing={1.5} sx={{ mt: 1.5 }}>
            {behavior.requiredSpecialistBundles.map((required) => {
              const selected = selectedSpecialistBundles.find((bundle) => bundle.bundleId === required.bundleId)
              const installed = selected?.contentHash === required.contentHash
              return (
                <Box key={required.bundleId} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 1.5 }}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                    <Typography variant="subtitle2">{required.bundleId}</Typography>
                    <Chip size="small" label={installed ? 'Installed' : 'Required'} color={installed ? 'success' : 'warning'} />
                    {typeof selected?.marketplacePluginVersion === 'string' ? (
                      <Chip size="small" label={`Plugin ${selected.marketplacePluginVersion}`} variant="outlined" />
                    ) : null}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>
                    {required.contentHash}
                  </Typography>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mt: 0.75 }}>
                    {required.specialistRefs.map((ref) => <Chip key={ref} size="small" label={ref} variant="outlined" />)}
                    {required.chainRefs.map((ref) => <Chip key={ref} size="small" label={ref} color="secondary" variant="outlined" />)}
                  </Stack>
                </Box>
              )
            })}
          </Stack>
        )}
      </Box>

      <Divider />

      <Box component="section">
        <Typography variant="h6">Execution Extensions</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Extensions add separately verified execution lifecycles. Selecting one makes a matching reviewed runtime artifact mandatory.
        </Typography>
        {compatibleExtensions.length === 0 ? (
          <Alert severity="info">This behavior has no compatible execution extensions.</Alert>
        ) : (
          <Stack spacing={2}>
            {compatibleExtensions.map((extension) => (
              <Box key={extension.code} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 2 }}>
                <FormControlLabel
                  control={(
                    <Checkbox
                      checked={selections.executionExtensions.includes(extension.code)}
                      onChange={() => setSelections((current) => ({
                        ...current,
                        executionExtensions: toggleValue(current.executionExtensions, extension.code),
                      }))}
                      disabled={!canEdit}
                    />
                  )}
                  label={extension.name}
                />
                <Typography variant="body2" color="text.secondary">{extension.description}</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                  <Chip size="small" label={extension.maturity} color="warning" variant="outlined" />
                  {extension.verificationPackIds.map((pack) => <Chip size="small" key={pack} label={pack} variant="outlined" />)}
                </Stack>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                  {extension.availabilityMessage}
                </Typography>
              </Box>
            ))}
          </Stack>
        )}
      </Box>

      <Divider />

      <Box component="section">
        <Typography variant="h6">Immutable Runtime Contract</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
          <Chip label={`Schema ${behavior.schemaVersion}`} variant="outlined" />
          <Chip label={`Contract v${behavior.contractVersion}`} variant="outlined" />
          {behavior.requiredRuntimeCapabilities.map((capability) => (
            <Chip key={capability} label={capability} color="primary" variant="outlined" />
          ))}
          {behavior.requiredRuntimeMigrationIds.map((migration) => (
            <Chip key={migration} label={migration} color="secondary" variant="outlined" />
          ))}
        </Stack>
      </Box>
    </Stack>
  )
}

function SmartBrainConfiguration({
  value,
  disabled,
  onChange,
}: {
  value: SmartBrainSettings
  disabled: boolean
  onChange: (value: SmartBrainSettings) => void
}) {
  const updateTrigger = (index: number, patch: Partial<SmartBrainTrigger>) => {
    onChange({
      ...value,
      triggers: value.triggers.map((trigger, current) => current === index ? { ...trigger, ...patch } : trigger),
    })
  }
  const updateSchedule = (index: number, patch: Partial<SmartBrainSchedule>) => {
    onChange({
      ...value,
      schedules: value.schedules.map((schedule, current) => current === index ? { ...schedule, ...patch } : schedule),
    })
  }

  return (
    <Box component="section">
      <Typography variant="h6">Smart Brain Inputs and Delivery</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Register trusted CloudEvent types, durable schedules, and the server-owned result delivery path for this deployment.
      </Typography>

      <TextField
        label="Maximum event bytes"
        type="number"
        size="small"
        value={value.maxEventBytes}
        disabled={disabled}
        inputProps={{ min: 1024, max: 1048576, step: 1024 }}
        onChange={(event) => onChange({ ...value, maxEventBytes: Number(event.target.value) })}
        sx={{ width: 220, mb: 3 }}
      />

      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 1.5 }}>
        <Typography variant="subtitle1">Registered triggers</Typography>
        <Button
          size="small"
          startIcon={<AddRoundedIcon />}
          disabled={disabled || value.triggers.length >= 32}
          onClick={() => onChange({
            ...value,
            triggers: [...value.triggers, {
              code: '',
              name: '',
              eventTypes: '',
              enabled: true,
            }],
          })}
        >
          Add trigger
        </Button>
      </Stack>
      <Stack spacing={2}>
        {value.triggers.map((trigger, index) => (
          <Grid container spacing={1.5} alignItems="center" key={`${trigger.code}-${index}`}>
            <Grid item xs={12} md={2.5}>
              <TextField fullWidth size="small" label="Trigger code" value={trigger.code} disabled={disabled} onChange={(event) => updateTrigger(index, { code: event.target.value })} />
            </Grid>
            <Grid item xs={12} md={2.5}>
              <TextField fullWidth size="small" label="Display name" value={trigger.name} disabled={disabled} onChange={(event) => updateTrigger(index, { name: event.target.value })} />
            </Grid>
            <Grid item xs={12} md={5}>
              <TextField fullWidth size="small" label="Allowed CloudEvent types" value={trigger.eventTypes} disabled={disabled} onChange={(event) => updateTrigger(index, { eventTypes: event.target.value })} />
            </Grid>
            <Grid item xs={8} md={1.25}>
              <FormControlLabel control={<Switch checked={trigger.enabled} disabled={disabled} onChange={(_, checked) => updateTrigger(index, { enabled: checked })} />} label="On" />
            </Grid>
            <Grid item xs={4} md={0.75}>
              <Tooltip title="Remove trigger">
                <span>
                  <IconButton disabled={disabled || value.triggers.length === 1} onClick={() => onChange({ ...value, triggers: value.triggers.filter((_, current) => current !== index) })}>
                    <DeleteOutlineRoundedIcon />
                  </IconButton>
                </span>
              </Tooltip>
            </Grid>
          </Grid>
        ))}
      </Stack>

      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mt: 4, mb: 1.5 }}>
        <Typography variant="subtitle1">Schedules</Typography>
        <Button
          size="small"
          startIcon={<AddRoundedIcon />}
          disabled={disabled || value.schedules.length >= 16}
          onClick={() => onChange({
            ...value,
            schedules: [...value.schedules, {
              code: '',
              triggerCode: value.triggers[0]?.code ?? '',
              cron: '0 0 * * * ?',
              zoneId: 'UTC',
              enabled: true,
            }],
          })}
        >
          Add schedule
        </Button>
      </Stack>
      {value.schedules.length === 0 ? (
        <Alert severity="info">No schedule is registered. HTTPS CloudEvent triggers remain available.</Alert>
      ) : (
        <Stack spacing={2}>
          {value.schedules.map((schedule, index) => (
            <Grid container spacing={1.5} alignItems="center" key={`${schedule.code}-${index}`}>
              <Grid item xs={12} md={2}>
                <TextField fullWidth size="small" label="Schedule code" value={schedule.code} disabled={disabled} onChange={(event) => updateSchedule(index, { code: event.target.value })} />
              </Grid>
              <Grid item xs={12} md={2.5}>
                <TextField select fullWidth size="small" label="Trigger" value={schedule.triggerCode} disabled={disabled} onChange={(event) => updateSchedule(index, { triggerCode: event.target.value })}>
                  {value.triggers.map((trigger) => <MenuItem key={trigger.code} value={trigger.code}>{trigger.name || trigger.code}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={3}>
                <TextField fullWidth size="small" label="Quartz cron" value={schedule.cron} disabled={disabled} onChange={(event) => updateSchedule(index, { cron: event.target.value })} />
              </Grid>
              <Grid item xs={12} md={2}>
                <TextField fullWidth size="small" label="Time zone" value={schedule.zoneId} disabled={disabled} onChange={(event) => updateSchedule(index, { zoneId: event.target.value })} />
              </Grid>
              <Grid item xs={8} md={1.5}>
                <FormControlLabel control={<Switch checked={schedule.enabled} disabled={disabled} onChange={(_, checked) => updateSchedule(index, { enabled: checked })} />} label="On" />
              </Grid>
              <Grid item xs={4} md={1}>
                <Tooltip title="Remove schedule">
                  <span><IconButton disabled={disabled} onClick={() => onChange({ ...value, schedules: value.schedules.filter((_, current) => current !== index) })}><DeleteOutlineRoundedIcon /></IconButton></span>
                </Tooltip>
              </Grid>
            </Grid>
          ))}
        </Stack>
      )}

      <Typography variant="subtitle1" sx={{ mt: 4, mb: 1.5 }}>Result delivery</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <Select
          size="small"
          value={value.deliveryMode}
          disabled={disabled}
          onChange={(event) => onChange({ ...value, deliveryMode: event.target.value as SmartBrainSettings['deliveryMode'] })}
          sx={{ width: { xs: '100%', md: 220 } }}
        >
          <MenuItem value="POLL">Operation polling</MenuItem>
          <MenuItem value="SIGNED_WEBHOOK">Signed webhook</MenuItem>
        </Select>
        {value.deliveryMode === 'SIGNED_WEBHOOK' ? (
          <TextField
            fullWidth
            size="small"
            label="HTTPS callback URL"
            value={value.callbackUrl}
            disabled={disabled}
            onChange={(event) => onChange({ ...value, callbackUrl: event.target.value })}
          />
        ) : null}
      </Stack>
    </Box>
  )
}

function smartBrainError(settings: SmartBrainSettings | null, behaviorType: string | undefined): string | null {
  if (behaviorType !== 'SMART_BRAIN') return null
  if (!settings || settings.triggers.length === 0) return 'Register at least one Smart Brain trigger.'
  const codes = settings.triggers.map((trigger) => trigger.code.trim())
  if (codes.some((code) => !/^[a-z][a-z0-9-]{1,63}$/.test(code)) || new Set(codes).size !== codes.length) {
    return 'Trigger codes must be unique lower-kebab-case values.'
  }
  if (settings.triggers.some((trigger) => !trigger.name.trim() || !trigger.eventTypes.split(',').some((value) => value.trim()))) {
    return 'Every trigger requires a name and at least one CloudEvent type.'
  }
  if (settings.maxEventBytes < 1024 || settings.maxEventBytes > 1048576) {
    return 'Maximum event bytes must be between 1,024 and 1,048,576.'
  }
  if (settings.schedules.some((schedule) => (
    !/^[a-z][a-z0-9-]{1,63}$/.test(schedule.code.trim())
    || !codes.includes(schedule.triggerCode)
    || !schedule.cron.trim()
    || !schedule.zoneId.trim()
  ))) return 'Every schedule requires a valid code, trigger, cron expression, and time zone.'
  if (settings.deliveryMode === 'SIGNED_WEBHOOK' && !/^https:\/\/[^\s]+$/i.test(settings.callbackUrl.trim())) {
    return 'Signed webhook delivery requires an HTTPS callback URL.'
  }
  return null
}
