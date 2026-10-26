import type { CustomerStagingConsumerConfigurationRequest } from '../../api/platformApi'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'

export type AssignmentConfigurationSnapshot = {
  actorId: string; authenticationMode: 'SESSION' | 'SUPABASE'; customerId: string; consumerId: string
  offerId: string; offerFingerprint: string; entitlementRevision: number
  deploymentId: string; tenantId: string; targetProfileId: string; bindingRevision: number
  draftId: string; draftRevision: number; draftUpdatedAt: string
  draftWorkspaces: { installationEntityId: string; expectedRowVersion: number }[]
}
type Attempt = { kind: 'ATTEMPT'; at: string; operationKey: string; requestSha256: string; snapshot: AssignmentConfigurationSnapshot }
type Rejection = { kind: 'REJECTED'; at: string; operationKey: string; requestSha256: string
  httpStatus: 403; errorCode: 'SETTING_FORBIDDEN'; responseRejected: true }
type Review = { kind: 'REVIEWED'; at: string; operationKey: string; snapshot: AssignmentConfigurationSnapshot }
type Completion = { kind: 'COMPLETED'; at: string; operationKey: string; requestSha256: string; bindingRevision: number }
type Event = Attempt | Rejection | Review | Completion
export type AssignmentConfigurationIntent = {
  status: 'EMPTY' | 'UNKNOWN' | 'REJECTED' | 'REVIEWED' | 'COMPLETED'
  attempt: Attempt | null; review: Review | null; completedRevision: number | null; events: Event[]
}
export type AssignmentConfigurationIntents = {
  read: (customerId: string, consumerId: string) => AssignmentConfigurationIntent
  claim: (snapshot: AssignmentConfigurationSnapshot, request: CustomerStagingConsumerConfigurationRequest, current: () => boolean) => Promise<void>
  rejected: (snapshot: AssignmentConfigurationSnapshot, request: CustomerStagingConsumerConfigurationRequest,
    proof: { httpStatus: 403; errorCode: 'SETTING_FORBIDDEN'; responseRejected: true }) => Promise<void>
  reviewed: (snapshot: AssignmentConfigurationSnapshot, operationKey: string, current: () => boolean) => Promise<void>
  completed: (snapshot: AssignmentConfigurationSnapshot, request: CustomerStagingConsumerConfigurationRequest, bindingRevision: number) => Promise<void>
}
type StoragePort = Pick<Storage, 'getItem' | 'setItem' | 'key' | 'length'>
const prefix = 'platform.assignment-configuration.v1:'
const blocked = () => new Error('Assignment submission protection is unavailable. Reconcile the original intent; do not resubmit.')
const equal = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right)
const fields = (value: unknown, names: string[]): value is Record<string, unknown> => !!value && typeof value === 'object'
  && !Array.isArray(value) && equal(Object.keys(value).sort(), [...names].sort())
const id = (value: unknown) => typeof value === 'string' && value.length > 0 && value.length <= 256 && value === value.trim()
const revision = (value: unknown) => Number.isSafeInteger(value) && Number(value) >= 0 && Number(value) < Number.MAX_SAFE_INTEGER
const requestDigest = async (request: CustomerStagingConsumerConfigurationRequest) => {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(request)))
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
}
const validSnapshot = (value: unknown): value is AssignmentConfigurationSnapshot => {
  if (!fields(value, ['actorId', 'authenticationMode', 'customerId', 'consumerId', 'offerId', 'offerFingerprint', 'entitlementRevision',
    'deploymentId', 'tenantId', 'targetProfileId', 'bindingRevision', 'draftId', 'draftRevision', 'draftUpdatedAt', 'draftWorkspaces'])) return false
  const apps = value.draftWorkspaces
  return ['actorId', 'customerId', 'consumerId', 'offerId', 'offerFingerprint', 'deploymentId', 'tenantId', 'targetProfileId', 'draftId']
    .every(name => id(value[name])) && typeof value.authenticationMode === 'string' && isCustomerAuthenticationMode(value.authenticationMode)
    && ['entitlementRevision', 'bindingRevision', 'draftRevision'].every(name => revision(value[name]))
    && typeof value.draftUpdatedAt === 'string' && Number.isFinite(Date.parse(value.draftUpdatedAt))
    && Array.isArray(apps) && apps.length <= 100 && apps.every(app => fields(app, ['installationEntityId', 'expectedRowVersion'])
      && id(app.installationEntityId) && revision(app.expectedRowVersion))
    && new Set(apps.map(app => app.installationEntityId)).size === apps.length
}

export function sameAssignmentReadback(left: AssignmentConfigurationSnapshot, right: AssignmentConfigurationSnapshot) {
  // Authority/entitlement can be freshly reviewed; the original rejected command's resource and row fences cannot change.
  const fence = ({ actorId: _actor, authenticationMode: _mode, entitlementRevision: _entitlement, ...rest }: AssignmentConfigurationSnapshot) => rest
  return equal(fence(left), fence(right))
}

export function canStartAssignmentIntent(state: AssignmentConfigurationIntent, snapshot: AssignmentConfigurationSnapshot) {
  return state.status === 'EMPTY' || (state.status === 'REVIEWED' && equal(state.review?.snapshot, snapshot))
    || (state.status === 'COMPLETED' && state.completedRevision !== null && snapshot.bindingRevision > state.completedRevision)
}

export function createAssignmentConfigurationIntents(options: {
  storage?: () => StoragePort; exclusive?: (key: string, work: () => void) => Promise<void>
} = {}): AssignmentConfigurationIntents {
  const storage = options.storage ?? (() => window.localStorage)
  const exclusive = options.exclusive ?? (async (key, work) => {
    if (typeof navigator === 'undefined' || !navigator.locks) throw blocked()
    await navigator.locks.request(key, { mode: 'exclusive' }, work)
  })
  const keyFor = (customerId: string, consumerId: string) => {
    if (!id(customerId) || !id(consumerId)) throw blocked()
    return prefix + JSON.stringify([customerId, consumerId])
  }
  const read = (customerId: string, consumerId: string): AssignmentConfigurationIntent => {
    try {
      const port = storage(), key = keyFor(customerId, consumerId)
      // Previous releases stored only a digest, not a structured rejection. They remain UNKNOWN across every fence change.
      for (let index = 0; index < port.length; index++) {
        const legacy = port.key(index)
        if (!legacy?.startsWith('platform.command-hold.v1:["CONFIGURE_EXISTING_ASSIGNMENT"')) continue
        const scope = JSON.parse(legacy.slice('platform.command-hold.v1:'.length))
        if (!Array.isArray(scope)) throw blocked()
        if (scope[1] === customerId && scope[2] === consumerId) throw blocked()
      }
      const raw = port.getItem(key)
      const state: AssignmentConfigurationIntent = { status: 'EMPTY', attempt: null, review: null, completedRevision: null, events: [] }
      if (raw === null) return state
      const journal = JSON.parse(raw)
      if (!fields(journal, ['schema', 'customerId', 'consumerId', 'events']) || journal.schema !== 1
        || journal.customerId !== customerId || journal.consumerId !== consumerId || !Array.isArray(journal.events)
        || journal.events.length === 0 || journal.events.length > 1000) throw blocked()
      const operations = new Set<string>()
      for (const candidate of journal.events) {
        const event = candidate as Event
        if (!event || typeof event.at !== 'string' || !Number.isFinite(Date.parse(event.at))
          || typeof event.operationKey !== 'string' || !/^[A-Za-z0-9._:-]{1,128}$/.test(event.operationKey)) throw blocked()
        if (event.kind === 'ATTEMPT') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'requestSha256', 'snapshot']) || !validSnapshot(event.snapshot)
            || event.snapshot.customerId !== customerId || event.snapshot.consumerId !== consumerId
            || !/^[a-f0-9]{64}$/.test(event.requestSha256) || operations.has(event.operationKey)
            || !canStartAssignmentIntent(state, event.snapshot)) throw blocked()
          operations.add(event.operationKey); state.attempt = event; state.review = null; state.completedRevision = null; state.status = 'UNKNOWN'
        } else if (event.kind === 'REJECTED') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'requestSha256', 'httpStatus', 'errorCode', 'responseRejected'])
            || state.status !== 'UNKNOWN' || event.operationKey !== state.attempt?.operationKey
            || event.requestSha256 !== state.attempt.requestSha256 || event.httpStatus !== 403
            || event.errorCode !== 'SETTING_FORBIDDEN' || event.responseRejected !== true) throw blocked()
          state.status = 'REJECTED'
        } else if (event.kind === 'REVIEWED') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'snapshot']) || !validSnapshot(event.snapshot)
            || !['REJECTED', 'REVIEWED'].includes(state.status) || event.operationKey !== state.attempt?.operationKey
            || !sameAssignmentReadback(state.attempt.snapshot, event.snapshot)) throw blocked()
          state.status = 'REVIEWED'; state.review = event
        } else if (event.kind === 'COMPLETED') {
          if (!fields(event, ['kind', 'at', 'operationKey', 'requestSha256', 'bindingRevision']) || state.status !== 'UNKNOWN'
            || event.operationKey !== state.attempt?.operationKey || event.requestSha256 !== state.attempt.requestSha256
            || event.bindingRevision !== state.attempt.snapshot.bindingRevision + 1) throw blocked()
          state.completedRevision = event.bindingRevision; state.status = 'COMPLETED'
        } else throw blocked()
        state.events.push(event)
      }
      return state
    } catch { throw blocked() }
  }
  const append = async (snapshot: AssignmentConfigurationSnapshot, current: () => boolean,
    event: (state: AssignmentConfigurationIntent) => Event) => {
    if (!validSnapshot(snapshot)) throw blocked()
    const key = keyFor(snapshot.customerId, snapshot.consumerId)
    await exclusive(key, () => {
      if (!current()) throw new Error('Assignment authority changed. Review the original intent.')
      const state = read(snapshot.customerId, snapshot.consumerId), next = event(state)
      const raw = JSON.stringify({ schema: 1, customerId: snapshot.customerId, consumerId: snapshot.consumerId, events: [...state.events, next] })
      try {
        storage().setItem(key, raw)
        if (storage().getItem(key) !== raw) throw blocked()
        read(snapshot.customerId, snapshot.consumerId)
      } catch { throw blocked() }
    })
  }
  return { read, async claim(snapshot, request, current) {
    if (request.consumerId !== snapshot.consumerId || request.expectedBindingRevision !== snapshot.bindingRevision
      || request.targetProfileId !== snapshot.targetProfileId
      || !equal(request.expectedDraftWorkspaces.map(({ installationEntityId, expectedRowVersion }) => ({ installationEntityId, expectedRowVersion })), snapshot.draftWorkspaces)) throw blocked()
    const requestSha256 = await requestDigest(request)
    await append(snapshot, current, state => {
      if (!canStartAssignmentIntent(state, snapshot) || state.events.some(event => event.kind === 'ATTEMPT' && event.operationKey === request.operationKey)) {
        throw new Error('Assignment intent is held. Explicitly review its known rejection before a new intent.')
      }
      return { kind: 'ATTEMPT', at: new Date().toISOString(), operationKey: request.operationKey, requestSha256, snapshot: structuredClone(snapshot) }
    })
  }, async rejected(snapshot, request, proof) {
    if (proof.httpStatus !== 403 || proof.errorCode !== 'SETTING_FORBIDDEN' || proof.responseRejected !== true) throw blocked()
    const digest = await requestDigest(request)
    await append(snapshot, () => true, state => {
      if (state.status !== 'UNKNOWN' || state.attempt?.operationKey !== request.operationKey
        || state.attempt.requestSha256 !== digest || !equal(state.attempt.snapshot, snapshot)) throw blocked()
      return { kind: 'REJECTED', at: new Date().toISOString(), operationKey: request.operationKey,
        requestSha256: state.attempt.requestSha256, httpStatus: 403, errorCode: 'SETTING_FORBIDDEN', responseRejected: true }
    })
  }, async reviewed(snapshot, operationKey, current) {
    await append(snapshot, current, state => {
      if (!['REJECTED', 'REVIEWED'].includes(state.status) || state.attempt?.operationKey !== operationKey
        || !sameAssignmentReadback(state.attempt.snapshot, snapshot)) throw new Error('Only the exact known rejection and unchanged row fences can be reconciled.')
      return { kind: 'REVIEWED', at: new Date().toISOString(), operationKey, snapshot: structuredClone(snapshot) }
    })
  }, async completed(snapshot, request, bindingRevision) {
    const digest = await requestDigest(request)
    await append(snapshot, () => true, state => {
      if (state.status !== 'UNKNOWN' || state.attempt?.operationKey !== request.operationKey
        || state.attempt.requestSha256 !== digest || bindingRevision !== snapshot.bindingRevision + 1) throw blocked()
      return { kind: 'COMPLETED', at: new Date().toISOString(), operationKey: request.operationKey, requestSha256: digest, bindingRevision }
    })
  } }
}
