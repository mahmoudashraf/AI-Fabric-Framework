import type { CustomerPostProvisionResumeCommand, CustomerPostProvisionResumeSummary } from '../../api/platformApi'
import { createDurableCommandHolds } from './durableCommandHolds'

export type PostResumeContext = {
  customerId: string; offerId: string; offerFingerprint: string; deploymentId: string
  targetProfileId: string; reservationId: string; releaseId: string; revision: number; releaseStatus: string
}
type Claim = { kind: 'CLAIM'; at: string; operationKey: string; commandSha256: string; generation: string
  allocationId: string; context: PostResumeContext }
type Ack = { kind: 'ACK'; at: string; operationKey: string; commandSha256: string; receipt: CustomerPostProvisionResumeSummary }
type Event = Claim | Ack
type State = { events: Event[]; claim: Claim | null; receipt: CustomerPostProvisionResumeSummary | null }
type StoragePort = Pick<Storage, 'getItem' | 'setItem'>
const prefix = 'platform.post-resume-receipts.v1:'
const blocked = () => new Error('Setup submission protection is unavailable. Reconcile the original command; do not resubmit.')
const equal = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right)
const fields = (value: unknown, names: string[]): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
  && equal(Object.keys(value).sort(), [...names].sort())
const id = (value: unknown): value is string => typeof value === 'string' && value.length > 0 && value.length <= 256 && value === value.trim()
const rev = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) >= 0 && Number(value) < Number.MAX_SAFE_INTEGER
const date = (value: unknown) => typeof value === 'string' && Number.isFinite(Date.parse(value))
const op = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9._:-]{1,128}$/.test(value)
const digest = async (body: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(body))), byte => byte.toString(16).padStart(2, '0')).join('')
const snapshot = (c: PostResumeContext): PostResumeContext => ({ customerId: c.customerId, offerId: c.offerId, offerFingerprint: c.offerFingerprint,
  deploymentId: c.deploymentId, targetProfileId: c.targetProfileId, reservationId: c.reservationId, releaseId: c.releaseId,
  revision: c.revision, releaseStatus: c.releaseStatus })
const identity = (context: PostResumeContext) => {
  const { revision: _revision, releaseStatus: _status, ...rest } = snapshot(context)
  return rest
}
const validContext = (value: unknown): value is PostResumeContext => fields(value, ['customerId', 'offerId', 'offerFingerprint', 'deploymentId',
  'targetProfileId', 'reservationId', 'releaseId', 'revision', 'releaseStatus'])
  && ['customerId', 'offerId', 'offerFingerprint', 'deploymentId', 'targetProfileId', 'reservationId', 'releaseId'].every(key => id(value[key]))
  && rev(value.revision) && id(value.releaseStatus)
const canReview = (state: State, context: PostResumeContext) => context.releaseStatus === 'FAILED' && (!state.claim
  || (!!state.receipt && equal(identity(state.claim.context), identity(context)) && context.revision > state.receipt.revision))
const validReceipt = (value: unknown, claim: Claim): value is CustomerPostProvisionResumeSummary => fields(value, ['receiptId', 'reservationId',
  'generation', 'fromRevision', 'revision', 'releaseId', 'status', 'financialMutationPerformed', 'runtimeRedeployRequired', 'retainedAllocationIds', 'authorizedAt'])
  && id(value.receiptId) && value.reservationId === claim.context.reservationId && value.generation === claim.generation
  && value.fromRevision === claim.context.revision && value.revision === claim.context.revision + 1
  && value.releaseId === claim.context.releaseId && value.status === 'APPLY_REQUESTED'
  && value.financialMutationPerformed === false && value.runtimeRedeployRequired === false
  && equal(value.retainedAllocationIds, [claim.allocationId]) && date(value.authorizedAt)

/** Append-only local acknowledgements never release an UNKNOWN or legacy digest-only intent. */
export function createPostProvisionResumeHolds(options: {
  storage?: () => StoragePort; exclusive?: (key: string, work: () => void) => Promise<void>
} = {}) {
  const storage = options.storage ?? (() => window.localStorage)
  const legacy = createDurableCommandHolds({ storage })
  const exclusive = options.exclusive ?? (async (key, work) => {
    if (typeof navigator === 'undefined' || !navigator.locks) throw blocked()
    await navigator.locks.request(key, { mode: 'exclusive' }, work)
  })
  const keyFor = (context: PostResumeContext) => prefix + JSON.stringify([context.customerId, context.reservationId])
  const legacyScope = (context: PostResumeContext) => JSON.stringify(['POST_PROVISION_RESUME', context.customerId, context.reservationId])
  const lockFor = (context: PostResumeContext) => 'platform.command-hold.v1:' + legacyScope(context)
  const load = (input: PostResumeContext): State => {
    try {
      const context = snapshot(input)
      if (!validContext(context)) throw blocked()
      const state: State = { events: [], claim: null, receipt: null }, raw = storage().getItem(keyFor(context))
      const marker = legacy.read(legacyScope(context))
      if (raw === null) { if (marker) throw blocked(); return state }
      if (!marker) throw blocked()
      const journal = JSON.parse(raw)
      if (!fields(journal, ['schema', 'customerId', 'reservationId', 'events']) || journal.schema !== 1
        || journal.customerId !== context.customerId || journal.reservationId !== context.reservationId
        || !Array.isArray(journal.events) || journal.events.length === 0 || journal.events.length > 1000) throw blocked()
      const operations = new Set<string>(), receipts = new Set<string>()
      for (const event of journal.events as Event[]) {
        if (!date(event?.at) || !op(event.operationKey) || !/^[a-f0-9]{64}$/.test(event.commandSha256)) throw blocked()
        if (event.kind === 'CLAIM') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'commandSha256', 'generation', 'allocationId', 'context'])
            || !validContext(event.context) || event.context.customerId !== context.customerId || event.context.reservationId !== context.reservationId
            || !id(event.generation) || !id(event.allocationId) || !canReview(state, event.context) || operations.has(event.operationKey)
            || (state.receipt && (event.generation !== state.receipt.generation || event.allocationId !== state.receipt.retainedAllocationIds[0]))) throw blocked()
          operations.add(event.operationKey); state.claim = event; state.receipt = null
        } else if (event.kind === 'ACK') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'commandSha256', 'receipt']) || !state.claim || state.receipt
            || event.operationKey !== state.claim.operationKey || event.commandSha256 !== state.claim.commandSha256
            || !validReceipt(event.receipt, state.claim) || receipts.has(event.receipt.receiptId)) throw blocked()
          receipts.add(event.receipt.receiptId); state.receipt = event.receipt
        } else throw blocked()
        state.events.push(event)
      }
      const first = state.events[0]
      if (first.kind !== 'CLAIM' || marker.operationKey !== first.operationKey
        || marker.commandSha256 !== first.commandSha256 || marker.recordedAt !== first.at) throw blocked()
      return state
    } catch { throw blocked() }
  }
  const review = (context: PostResumeContext, command: CustomerPostProvisionResumeCommand, allocationId: string) => {
    const state = load(context)
    if (!canReview(state, context) || !id(allocationId) || !op(command.operationKey) || !id(command.fence?.generation)
      || command.fence.reservationId !== context.reservationId || command.fence.revision !== context.revision || command.releaseId !== context.releaseId
      || command.acceptRemainingDependencyProvisioning !== true || !/^[a-f0-9]{64}$/.test(command.proofFingerprint)
      || state.events.some(event => event.operationKey === command.operationKey)
      || (state.receipt && (command.fence.generation !== state.receipt.generation || allocationId !== state.receipt.retainedAllocationIds[0]))) {
      throw new Error('Setup intent remains held. A known acknowledgement, later reservation fence and the same FAILED release are required.')
    }
    return state
  }
  const append = (context: PostResumeContext, state: State, event: Event) => {
    if (state.events.length >= 1000) throw blocked()
    const raw = JSON.stringify({ schema: 1, customerId: context.customerId, reservationId: context.reservationId, events: [...state.events, event] })
    try { storage().setItem(keyFor(context), raw); if (storage().getItem(keyFor(context)) !== raw) throw blocked(); load(context) }
    catch { throw blocked() }
  }
  return {
    read(context: PostResumeContext) { return { held: !canReview(load(context), context) } },
    review(context: PostResumeContext, command: CustomerPostProvisionResumeCommand, allocationId: string) { review(context, command, allocationId) },
    async claim(input: PostResumeContext, command: CustomerPostProvisionResumeCommand, allocationId: string, current: () => boolean) {
      const context = snapshot(input), commandSha256 = await digest(JSON.stringify(command))
      await exclusive(lockFor(context), () => {
        if (!current()) throw new Error('Setup authority changed. Review the original intent.')
        const state = review(context, command, allocationId)
        const event: Claim = { kind: 'CLAIM', at: new Date().toISOString(), operationKey: command.operationKey, commandSha256,
          generation: command.fence.generation, allocationId, context }
        if (state.events.length === 0) {
          // Older tabs keep seeing the immutable first hold; only a matching receipt journal can acknowledge it.
          const marker = JSON.stringify({ schema: 1, scope: legacyScope(context), operationKey: event.operationKey,
            commandSha256, recordedAt: event.at })
          try { storage().setItem(lockFor(context), marker); if (storage().getItem(lockFor(context)) !== marker) throw blocked() }
          catch { throw blocked() }
        }
        append(context, state, event)
      })
    },
    async acknowledge(input: PostResumeContext, command: CustomerPostProvisionResumeCommand, receipt: CustomerPostProvisionResumeSummary) {
      const context = snapshot(input), commandSha256 = await digest(JSON.stringify(command))
      await exclusive(lockFor(context), () => {
        const state = load(context)
        if (!state.claim || state.claim.operationKey !== command.operationKey || state.claim.commandSha256 !== commandSha256
          || !equal(state.claim.context, context) || !validReceipt(receipt, state.claim)) throw blocked()
        if (state.receipt) { if (!equal(state.receipt, receipt)) throw blocked(); return }
        append(context, state, { kind: 'ACK', at: new Date().toISOString(), operationKey: command.operationKey, commandSha256, receipt: structuredClone(receipt) })
      })
    },
  }
}
