import { Alert, Button, Checkbox, Divider, FormControlLabel, Stack, TextField, Typography } from '@mui/material'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { planCustomerKnownNoEffectSettlement, settleCustomerKnownNoEffectReservation,
  type CustomerAdmissionReservationSummary, type CustomerKnownNoEffectSettlementPlan,
  type CustomerKnownNoEffectSettlementPlanRequest, type CustomerKnownNoEffectSettlementSummary,
  type CustomerUnusedDraftWorkspaceExpectation } from '../../api/platformApi'
import { createDurableCommandHolds } from './durableCommandHolds'

export type KnownNoEffectSettlementAuthority = {
  actorId: string
  customerId: string
  authenticationMode: string | null
}

export type KnownNoEffectSettlementScope = {
  authority: KnownNoEffectSettlementAuthority
  offerId: string
  deploymentId: string
  reservationId: string
  targetProfileId: string
  revision: number
}

type PreparedSettlement = {
  scope: KnownNoEffectSettlementScope
  plan: CustomerKnownNoEffectSettlementPlan
  commandBody: string
}

const text = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
const revision = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) >= 0
const identity = (scope: KnownNoEffectSettlementScope) => JSON.stringify([
  scope.authority.customerId, scope.offerId, scope.deploymentId, scope.reservationId, scope.targetProfileId,
])
const sameScope = (left: KnownNoEffectSettlementScope, right: KnownNoEffectSettlementScope) =>
  identity(left) === identity(right) && left.revision === right.revision
  && left.authority.actorId === right.authority.actorId && left.authority.authenticationMode === right.authority.authenticationMode
const durableScope = (scope: KnownNoEffectSettlementScope) => JSON.stringify(['KNOWN_NO_EFFECT_SETTLEMENT', identity(scope)])
const unique = (values: string[]) => new Set(values).size === values.length
const validDrafts = (values: CustomerUnusedDraftWorkspaceExpectation[] | null) => Array.isArray(values)
  && values.every((value) => value && text(value.installationEntityId) && revision(value.expectedRowVersion))
  && unique(values.map((value) => value.installationEntityId))

export function prepareKnownNoEffectSettlement(scope: KnownNoEffectSettlementScope,
  request: CustomerKnownNoEffectSettlementPlanRequest, response: CustomerKnownNoEffectSettlementPlan): PreparedSettlement {
  const command = response?.command
  if (!text(response?.proofFingerprint) || !command || !command.fence
    || !text(scope.authority?.actorId) || !text(scope.authority?.customerId) || scope.authority.authenticationMode !== 'SESSION'
    || ![scope.offerId, scope.deploymentId, scope.reservationId, scope.targetProfileId, request.operationKey, request.reason].every(text)
    || request.operationKey.length > 128 || request.reason.length > 1000
    || !revision(scope.revision) || scope.revision === Number.MAX_SAFE_INTEGER || command.fence.reservationId !== scope.reservationId
    || command.fence.revision !== scope.revision || !text(command.fence.generation)
    || command.operationKey !== request.operationKey || command.reason !== request.reason
    || ![command.manifestFingerprint, command.versionId, command.versionConfigHash, command.bindingFingerprint].every(text)
    || !Array.isArray(command.failedReleases) || command.failedReleases.length === 0
    || !command.failedReleases.every((item) => item && text(item.releaseId) && text(item.authorizationOperationKey)
      && text(item.authorizationRequestFingerprint)) || !unique(command.failedReleases.map((item) => item.releaseId))
    || !Array.isArray(command.consumerDetachments)
    || !command.consumerDetachments.every((item) => item && text(item.consumerEntityId)
      && revision(item.expectedBindingRevision) && item.expectedBindingRevision < Number.MAX_SAFE_INTEGER && validDrafts(item.draftWorkspaces))
    || !unique(command.consumerDetachments.map((item) => item.consumerEntityId))) {
    throw new Error('The settlement preview does not match this reservation. Refresh and review its current state.')
  }
  const plan = structuredClone(response)
  return { scope: structuredClone(scope), plan, commandBody: JSON.stringify(plan.command) }
}

/** Claim before dispatch, including ambiguous responses; this mounted panel never retries a settlement. */
export function claimKnownNoEffectSettlement(scope: KnownNoEffectSettlementScope, prepared: PreparedSettlement,
  confirmed: boolean, attempted: Set<string>) {
  if (!confirmed || !sameScope(scope, prepared.scope) || JSON.stringify(prepared.plan.command) !== prepared.commandBody) {
    throw new Error('Confirm the current unchanged settlement preview before submitting.')
  }
  const key = identity(scope)
  if (attempted.has(key)) throw new Error('Settlement was already submitted. Reconcile the reservation before any further action.')
  attempted.add(key)
  return prepared.plan.command
}

export function checkedKnownNoEffectSettlementReceipt(prepared: PreparedSettlement,
  receipt: CustomerKnownNoEffectSettlementSummary): CustomerKnownNoEffectSettlementSummary {
  const { scope, plan: { command } } = prepared
  if (!receipt || !text(receipt.id) || receipt.reservationId !== scope.reservationId
    || receipt.deploymentId !== scope.deploymentId || receipt.targetProfileId !== scope.targetProfileId
    || receipt.generation !== command.fence.generation || receipt.fromRevision !== scope.revision
    || !revision(receipt.revision) || receipt.revision !== scope.revision + 1
    || receipt.outcome !== 'SETTLED_KNOWN_NO_EFFECT' || receipt.reclamationPerformed !== false
    || !text(receipt.settledAt) || !Number.isFinite(Date.parse(receipt.settledAt))
    || JSON.stringify(receipt.failedReleaseIds) !== JSON.stringify(command.failedReleases.map((item) => item.releaseId))
    || !Array.isArray(receipt.consumerDetachments) || receipt.consumerDetachments.length !== command.consumerDetachments.length
    || !unique(receipt.consumerDetachments.map((item) => item?.consumerEntityId))
    || !command.consumerDetachments.every((expected) => {
      const actual = receipt.consumerDetachments.find((item) => item?.consumerEntityId === expected.consumerEntityId)
      return actual && actual.fromBindingRevision === expected.expectedBindingRevision
        && actual.toBindingRevision === expected.expectedBindingRevision + 1
        && text(actual.historyId) && text(actual.operationKey)
        && JSON.stringify(actual.preservedDraftWorkspaces) === JSON.stringify(expected.draftWorkspaces)
    })) {
    throw new Error('Settlement response is unconfirmed. Do not resubmit; reconcile the reservation.')
  }
  return receipt
}

type PanelProps = {
  authority: KnownNoEffectSettlementAuthority
  targetProfileId: string
  offerId: string
  deploymentId: string
  reservation: CustomerAdmissionReservationSummary | null
  candidateFailed: boolean
  onSettled: (receipt: CustomerKnownNoEffectSettlementSummary, scope: KnownNoEffectSettlementScope) => void | Promise<void>
}

export function KnownNoEffectSettlementPanel(props: PanelProps) {
  const requests = useRef(new Map<string, CustomerKnownNoEffectSettlementPlanRequest>())
  const attempted = useRef(new Set<string>())
  const { authority, targetProfileId, offerId, deploymentId, reservation, candidateFailed } = props
  const scopeKey = JSON.stringify([authority, targetProfileId, offerId, deploymentId, reservation, candidateFailed])
  const currentScope = useRef(scopeKey)
  currentScope.current = scopeKey
  if (!authority || !text(authority.actorId) || !text(authority.customerId) || authority.authenticationMode !== 'SESSION'
    || !reservation || reservation.targetProfileId !== targetProfileId || reservation.deploymentId !== deploymentId || !text(offerId) || !candidateFailed
    || !text(reservation.id) || !text(reservation.targetProfileId) || !revision(reservation.revision)
    || reservation.state !== 'RESERVED') return null
  const scope = { authority: { ...authority }, offerId, deploymentId, reservationId: reservation.id,
    targetProfileId: reservation.targetProfileId, revision: reservation.revision }
  return <SettlementReview key={scopeKey} scope={scope} requests={requests.current} attempted={attempted.current}
    isCurrent={() => currentScope.current === scopeKey} onSettled={props.onSettled} />
}

function SettlementReview({ scope, requests, attempted, isCurrent, onSettled }: {
  scope: KnownNoEffectSettlementScope
  requests: Map<string, CustomerKnownNoEffectSettlementPlanRequest>
  attempted: Set<string>
  isCurrent: () => boolean
  onSettled: PanelProps['onSettled']
}) {
  const queryClient = useQueryClient()
  const holds = useRef(createDurableCommandHolds())
  const requestKey = JSON.stringify([scope.authority, identity(scope), scope.revision])
  const [reason, setReason] = useState(requests.get(requestKey)?.reason ?? '')
  const [prepared, setPrepared] = useState<PreparedSettlement | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [pending, setPending] = useState<'preview' | 'settle' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [receipt, setReceipt] = useState<CustomerKnownNoEffectSettlementSummary | null>(null)
  const busy = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const current = () => mounted.current && isCurrent()
  let protectionError: string | null = null
  let durableSubmitted = false
  try { durableSubmitted = Boolean(holds.current.read(durableScope(scope))) }
  catch (failure) { protectionError = failure instanceof Error ? failure.message : 'Submission protection is unavailable.' }
  const submitted = attempted.has(identity(scope)) || durableSubmitted

  async function preview() {
    if (busy.current || submitted || protectionError || !current() || !reason.trim()) return
    busy.current = true
    setPending('preview'); setError(null); setPrepared(null); setConfirmed(false)
    let request = requests.get(requestKey)
    if (!request) {
      request = { operationKey: crypto.randomUUID(), reason: reason.trim() }
      requests.set(requestKey, request)
      setReason(request.reason)
    }
    try {
      const plan = await planCustomerKnownNoEffectSettlement(scope.offerId, scope.deploymentId, scope.reservationId, request)
      const reviewed = prepareKnownNoEffectSettlement(scope, request, plan)
      if (current()) setPrepared(reviewed)
    } catch (failure) {
      if (current()) setError(failure instanceof Error ? failure.message : 'Settlement preview unavailable.')
    } finally {
      busy.current = false
      if (current()) setPending(null)
    }
  }

  async function settle() {
    if (busy.current || submitted || protectionError || !current() || !prepared || !confirmed) return
    let command
    try { command = claimKnownNoEffectSettlement(scope, prepared, confirmed, attempted) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Settlement is not confirmed.'); return }
    busy.current = true
    setPending('settle'); setError(null)
    let accepted: CustomerKnownNoEffectSettlementSummary | null = null
    let dispatched = false
    try {
      await holds.current.claim(durableScope(scope), command.operationKey, prepared.commandBody, current)
      if (!current()) return
      dispatched = true
      const response = await settleCustomerKnownNoEffectReservation(scope.offerId, scope.deploymentId, scope.reservationId, command)
      accepted = checkedKnownNoEffectSettlementReceipt(prepared, response)
      if (current()) setReceipt(accepted)
    } catch (failure) {
      if (current()) setError(dispatched ? 'Settlement response is unconfirmed. Do not resubmit; reconcile the reservation.'
        : failure instanceof Error ? failure.message : 'Submission blocked. Reconcile the command before further action.')
    } finally {
      // Scope was captured before dispatch; a later target selection must not redirect invalidation.
      if (dispatched) void queryClient.invalidateQueries({ queryKey: ['customer-admission-reservations', scope.offerId,
        scope.deploymentId, scope.targetProfileId], exact: true }).catch(() => undefined)
      busy.current = false
      if (current()) setPending(null)
    }
    if (accepted) {
      try { await onSettled(accepted, structuredClone(scope)) }
      catch { if (current()) setError('Settlement receipt recorded; reservation refresh is incomplete.') }
    }
  }

  return <Stack spacing={2} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
    <Typography variant="subtitle2">Failed release settlement</Typography>
    <Typography variant="caption">{scope.reservationId} · {scope.targetProfileId} · revision {scope.revision}</Typography>
    {error ? <Alert severity="warning">{error}</Alert> : null}
    {protectionError ? <Alert severity="warning">{protectionError}</Alert> : null}
    {receipt ? <Alert severity="success">Settlement receipt {receipt.id} · no resources reclaimed</Alert> : null}
    {!receipt && submitted && !pending && !error
      ? <Alert severity="warning">Settlement already submitted. Reconcile this reservation before further action.</Alert> : null}
    <TextField label="Settlement reason" value={reason} multiline minRows={2} inputProps={{ maxLength: 1000 }}
      disabled={!!pending || requests.has(requestKey) || submitted || !!protectionError}
      onChange={(event) => setReason(event.target.value)} />
    {!prepared ? <Button startIcon={<FactCheckOutlinedIcon />} variant="outlined" sx={{ alignSelf: 'flex-start' }}
      disabled={!!pending || submitted || !!protectionError || !reason.trim()} onClick={() => void preview()}>
      {pending === 'preview' ? 'Preparing preview' : 'Preview settlement'}
    </Button> : <>
      <Divider />
      <Typography variant="body2">Failed releases</Typography>
      {prepared.plan.command.failedReleases.map((item) => <Typography key={item.releaseId} variant="body2">{item.releaseId}</Typography>)}
      <Typography variant="body2">Consumer detachments</Typography>
      {prepared.plan.command.consumerDetachments.length === 0 ? <Typography variant="body2">None</Typography> : null}
      {prepared.plan.command.consumerDetachments.map((item) => <Stack key={item.consumerEntityId} spacing={0.5}>
        <Typography variant="body2">{item.consumerEntityId} · binding revision {item.expectedBindingRevision}</Typography>
        {(item.draftWorkspaces ?? []).map((draft) => <Typography key={draft.installationEntityId} variant="caption">
          Preserved DRAFT: {draft.installationEntityId} · row version {draft.expectedRowVersion}
        </Typography>)}
      </Stack>)}
      <FormControlLabel label="I confirm these failed releases and consumer detachments." control={<Checkbox
        checked={confirmed} disabled={!!pending || submitted || !!protectionError} onChange={(event) => setConfirmed(event.target.checked)} />} />
      <Button startIcon={<CheckCircleOutlineRoundedIcon />} variant="outlined" sx={{ alignSelf: 'flex-start' }}
        disabled={!!pending || submitted || !!protectionError || !confirmed} onClick={() => void settle()}>
        {pending === 'settle' ? 'Submitting settlement' : 'Settle failed reservation'}
      </Button>
    </>}
  </Stack>
}
