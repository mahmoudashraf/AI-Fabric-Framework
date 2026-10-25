import type { AIWorkspaceInstallationSummary, CustomerDeploymentSummary, CustomerOfferSummary,
  CustomerStagingConsumerConfigurationRequest, DeploymentDraftResponse, DeploymentReleaseSummary, DeploymentVersionSummary,
  PlatformConsumerSummary, PlatformAuthSessionSummary } from '../../api/platformApi'
import type { AssignmentConfigurationIntents, AssignmentConfigurationSnapshot } from './customerAssignmentConfigurationIntents'
import { sameAssignmentReadback } from './customerAssignmentConfigurationIntents'

export function selectableCustomerAssignments(
  consumers: PlatformConsumerSummary[], customerId: string, deploymentId: string,
): PlatformConsumerSummary[] {
  if (!customerId || !deploymentId) return []
  const owned = consumers.filter((consumer) => consumer.customerId === customerId && consumer.status === 'ACTIVE')
  return [
    ...owned.filter((consumer) => consumer.boundDeploymentId === deploymentId),
    ...owned.filter((consumer) => consumer.boundDeploymentId === null
      && consumer.boundReleaseId === null && consumer.boundTargetProfileId === null),
  ]
}

export type CustomerAssignmentConfigurationScope = {
  actorId: string
  authenticationMode: string | null
  customerId: string
  offer: CustomerOfferSummary
  deployment: CustomerDeploymentSummary
  consumer: PlatformConsumerSummary
  draft?: DeploymentDraftResponse
}

const safeRevision = (value: number) => Number.isSafeInteger(value) && value >= 0 && value < Number.MAX_SAFE_INTEGER
const fullyUnbound = (consumer: PlatformConsumerSummary) => consumer.boundDeploymentId === null
  && consumer.boundReleaseId === null && consumer.boundTargetProfileId === null

export function canReviewCustomerAssignment(scope: CustomerAssignmentConfigurationScope,
  versions: DeploymentVersionSummary[], releases: DeploymentReleaseSummary[]) {
  const { actorId, authenticationMode, customerId, offer, deployment, consumer } = scope
  const target = offer.targets.find((item) => item.id === deployment.bootstrapTargetProfileId)
  return Boolean(actorId && authenticationMode === 'SESSION' && customerId && offer.entitlement.customerId === customerId
    && offer.entitlement.status === 'ACTIVE' && safeRevision(offer.entitlement.revision)
    && deployment.offerId === offer.id && deployment.offerFingerprint === offer.fingerprint
    && deployment.status === 'DRAFT' && deployment.activeVersionId === null
    && target?.intent === 'CUSTOMER_STAGING' && target.capabilities.includes('APP_CONFIGURE')
    && consumer.customerId === customerId && consumer.status === 'ACTIVE' && fullyUnbound(consumer)
    && safeRevision(consumer.bindingRevision) && versions.length === 0 && releases.length === 0)
}

export function canConfigureExistingCustomerAssignment(scope: CustomerAssignmentConfigurationScope,
  versions: DeploymentVersionSummary[], releases: DeploymentReleaseSummary[]) {
  return canReviewCustomerAssignment(scope, versions, releases) && scope.draft?.deploymentId === scope.deployment.deploymentId
    && ['DRAFT', 'MODIFIED'].includes(scope.draft.status) && Boolean(scope.draft.id)
}

export function customerAssignmentDraftExpectations(scope: CustomerAssignmentConfigurationScope,
  installations: AIWorkspaceInstallationSummary[])
  : CustomerStagingConsumerConfigurationRequest['expectedDraftWorkspaces'] {
  if (!canConfigureExistingCustomerAssignment(scope, [], [])) {
    throw new Error('An owned, active, fully unassigned app and unused customer-staging configuration are required.')
  }
  // Include every linked app, then validate; filtering out activated or disabled apps would hide an unsafe fence.
  const linked = installations.filter((item) => item.consumerId === scope.consumer.consumerId)
  if (linked.length > 100 || linked.some((item) => item.customerId !== scope.customerId || item.status !== 'DRAFT'
    || item.activatedAt !== null || item.disabledAt !== null || typeof item.id !== 'string' || !item.id.trim()
    || item.id !== item.id.trim() || item.id.length > 64 || /[\x00-\x1f\x7f]/.test(item.id)
    || !safeRevision(item.rowVersion)) || new Set(linked.map((item) => item.id)).size !== linked.length) {
    throw new Error('All linked workspaces must be current owned, never-activated DRAFTs with complete row versions.')
  }
  return linked.map((item) => ({ installationEntityId: item.id, expectedRowVersion: item.rowVersion }))
    .sort((left, right) => left.installationEntityId < right.installationEntityId ? -1
      : left.installationEntityId > right.installationEntityId ? 1 : 0)
}

export function customerAssignmentConfigurationRequest(scope: CustomerAssignmentConfigurationScope,
  installations: AIWorkspaceInstallationSummary[], operationKey: string): CustomerStagingConsumerConfigurationRequest {
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(operationKey)) throw new Error('A stable configuration operation key is required.')
  return { consumerId: scope.consumer.consumerId, expectedBindingRevision: scope.consumer.bindingRevision,
    targetProfileId: scope.deployment.bootstrapTargetProfileId, operationKey,
    expectedDraftWorkspaces: customerAssignmentDraftExpectations(scope, installations) }
}

export function assignmentConfigurationClaimKey(scope: CustomerAssignmentConfigurationScope,
  _workspaces?: CustomerStagingConsumerConfigurationRequest['expectedDraftWorkspaces']) {
  return JSON.stringify(['CONFIGURE_EXISTING_ASSIGNMENT', scope.customerId, scope.consumer.consumerId])
}

export function assignmentConfigurationSnapshot(scope: CustomerAssignmentConfigurationScope,
  workspaces: CustomerStagingConsumerConfigurationRequest['expectedDraftWorkspaces']): AssignmentConfigurationSnapshot {
  if (!scope.draft || scope.authenticationMode !== 'SESSION') throw new Error('Current editable draft and session authority are required.')
  return { actorId: scope.actorId, authenticationMode: 'SESSION', customerId: scope.customerId, consumerId: scope.consumer.consumerId,
    offerId: scope.offer.id, offerFingerprint: scope.offer.fingerprint, entitlementRevision: scope.offer.entitlement.revision,
    deploymentId: scope.deployment.deploymentId, tenantId: scope.deployment.tenantId, targetProfileId: scope.deployment.bootstrapTargetProfileId,
    bindingRevision: scope.consumer.bindingRevision, draftId: scope.draft.id, draftRevision: scope.draft.revisionNumber,
    draftUpdatedAt: scope.draft.updatedAt,
    draftWorkspaces: workspaces.map(({ installationEntityId, expectedRowVersion }) => ({ installationEntityId, expectedRowVersion })) }
}

export type ConfigurationApi = {
  session: () => Promise<PlatformAuthSessionSummary>
  offer: (offerId: string) => Promise<CustomerOfferSummary>
  deployments: () => Promise<CustomerDeploymentSummary[]>
  installations: (customerId: string) => Promise<AIWorkspaceInstallationSummary[]>
  consumers: (customerId: string) => Promise<PlatformConsumerSummary[]>
  versions: (deploymentId: string) => Promise<DeploymentVersionSummary[]>
  releases: (deploymentId: string) => Promise<DeploymentReleaseSummary[]>
  draft: (deploymentId: string) => Promise<DeploymentDraftResponse>
  configure: (offerId: string, deploymentId: string, body: CustomerStagingConsumerConfigurationRequest) => Promise<PlatformConsumerSummary>
}

async function freshAssignmentConfiguration(api: ConfigurationApi, scope: CustomerAssignmentConfigurationScope, stillCurrent: () => boolean) {
  const [session, offer, deployments, installations, consumers, versions, releases, draft] = await Promise.all([
    api.session(), api.offer(scope.offer.id), api.deployments(), api.installations(scope.customerId), api.consumers(scope.customerId),
    api.versions(scope.deployment.deploymentId), api.releases(scope.deployment.deploymentId), api.draft(scope.deployment.deploymentId),
  ])
  const matches = consumers.filter(item => item.consumerId === scope.consumer.consumerId)
  const owned = deployments.filter(item => item.deploymentId === scope.deployment.deploymentId)
  const fresh = matches.length === 1 && owned.length === 1 ? { ...scope, offer, deployment: owned[0], consumer: matches[0], draft } : null
  if (!stillCurrent() || !session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || session.actorId !== scope.actorId || session.customerId !== scope.customerId || !fresh
    || offer.id !== scope.offer.id || offer.fingerprint !== scope.offer.fingerprint || offer.entitlement.revision !== scope.offer.entitlement.revision
    || fresh.deployment.tenantId !== scope.deployment.tenantId || fresh.deployment.bootstrapTargetProfileId !== scope.deployment.bootstrapTargetProfileId
    || !canConfigureExistingCustomerAssignment(fresh, versions, releases) || !scope.draft || draft.id !== scope.draft.id
    || draft.revisionNumber !== scope.draft.revisionNumber || draft.updatedAt !== scope.draft.updatedAt) {
    throw new Error('Assignment or workspace configuration changed. Refresh and review before configuring.')
  }
  return { scope: fresh, installations }
}

export type KnownAssignmentReconciliation = { snapshot: AssignmentConfigurationSnapshot; operationKey: string; rejectedRequestSha256: string }

export async function prepareKnownAssignmentReconciliation(api: ConfigurationApi, scope: CustomerAssignmentConfigurationScope,
  stillCurrent: () => boolean, intents: AssignmentConfigurationIntents): Promise<KnownAssignmentReconciliation> {
  const original = intents.read(scope.customerId, scope.consumer.consumerId)
  if (!['REJECTED', 'REVIEWED'].includes(original.status) || !original.attempt) throw new Error('An UNKNOWN assignment intent cannot be released.')
  const fresh = await freshAssignmentConfiguration(api, scope, stillCurrent)
  const snapshot = assignmentConfigurationSnapshot(fresh.scope, customerAssignmentDraftExpectations(fresh.scope, fresh.installations))
  const after = intents.read(scope.customerId, scope.consumer.consumerId)
  if (!stillCurrent() || after.attempt?.operationKey !== original.attempt.operationKey
    || !['REJECTED', 'REVIEWED'].includes(after.status) || !sameAssignmentReadback(original.attempt.snapshot, snapshot)) {
    throw new Error('The rejected assignment or its complete original row fences changed. Keep the intent held.')
  }
  return { snapshot, operationKey: original.attempt.operationKey, rejectedRequestSha256: original.attempt.requestSha256 }
}

export async function confirmKnownAssignmentReconciliation(api: ConfigurationApi, scope: CustomerAssignmentConfigurationScope,
  reviewed: KnownAssignmentReconciliation, stillCurrent: () => boolean, intents: AssignmentConfigurationIntents) {
  const fresh = await prepareKnownAssignmentReconciliation(api, scope, stillCurrent, intents)
  if (JSON.stringify(fresh) !== JSON.stringify(reviewed)) throw new Error('The reviewed readback changed. Review the known rejection again.')
  await intents.reviewed(fresh.snapshot, fresh.operationKey, stillCurrent)
}

export async function configureExistingCustomerAssignment(api: ConfigurationApi,
  scope: CustomerAssignmentConfigurationScope, request: CustomerStagingConsumerConfigurationRequest,
  stillCurrent: () => boolean, intents: AssignmentConfigurationIntents) {
  const command = structuredClone(request)
  const intent = intents.read(scope.customerId, scope.consumer.consumerId)
  if (!['EMPTY', 'REVIEWED', 'COMPLETED'].includes(intent.status)) throw new Error('Assignment intent is held. Reconcile the original command before any further action.')
  if (!stillCurrent()) throw new Error('Assignment scope changed. Review its current configuration.')
  const fresh = await freshAssignmentConfiguration(api, scope, stillCurrent)
  if (JSON.stringify(customerAssignmentConfigurationRequest(fresh.scope, fresh.installations, command.operationKey)) !== JSON.stringify(command)
    || !stillCurrent()) throw new Error('Assignment or workspace configuration changed. Refresh and review before configuring.')
  const snapshot = assignmentConfigurationSnapshot(fresh.scope, command.expectedDraftWorkspaces)
  await intents.claim(snapshot, command, stillCurrent)
  if (!stillCurrent()) throw new Error('Assignment scope changed. Reconcile the recorded submission intent.')
  try {
    const result = await api.configure(scope.offer.id, scope.deployment.deploymentId, command)
    if (!result || result.consumerId !== scope.consumer.consumerId || result.customerId !== scope.customerId
      || result.status !== 'ACTIVE' || result.boundDeploymentId !== scope.deployment.deploymentId
      || result.boundTargetProfileId !== scope.deployment.bootstrapTargetProfileId || result.boundReleaseId !== null
      || result.bindingRevision !== command.expectedBindingRevision + 1) throw new Error('Unconfirmed configuration result')
    await intents.completed(snapshot, command, result.bindingRevision)
    return result
  } catch (failure) {
    if (failure instanceof Error && 'status' in failure && failure.status === 403
      && 'errorCode' in failure && failure.errorCode === 'SETTING_FORBIDDEN'
      && 'responseRejected' in failure && failure.responseRejected === true) {
      await intents.rejected(snapshot, command, { httpStatus: 403, errorCode: 'SETTING_FORBIDDEN', responseRejected: true })
      throw new Error('Configuration rejected (SETTING_FORBIDDEN). Explicitly review the known rejection and current DRAFT fences before a new intent.')
    }
    throw new Error('Assignment configuration response is unconfirmed. Reconcile its binding; do not resubmit.')
  }
}
