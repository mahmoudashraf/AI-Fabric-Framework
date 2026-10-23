import type { CustomerDeploymentSummary, CustomerOfferSummary, DeploymentMarketplaceInstallSummary, DeploymentSummary, PlatformAuthSessionSummary } from '../../api/platformApi'

export function hasReviewedDocumentStorageInstall(offer: CustomerOfferSummary | null, installs: DeploymentMarketplaceInstallSummary[]) {
  return Boolean(offer?.bindingSlots.some(slot => slot.kind === 'CUSTOMER_S3' && slot.supportedProfileIds.length > 0
    && slot.pluginId && installs.some(install => install.pluginId === slot.pluginId)))
}

export type CustomerDocumentStorageScope = {
  actorId: string; authenticationMode: 'SESSION'; customerId: string
  deploymentId: string; tenantId: string; offerId: string; offerFingerprint: string
  entitlementRevision: number; targetProfileId: string; bindingSlotId: string
  operation: 'BINDING_REPLACE' | 'BINDING_REVOKE'; selectedProfileId?: string
}

export function customerDocumentStorageScope(input: {
  session: PlatformAuthSessionSummary | null; deployment: CustomerDeploymentSummary | null
  selectedDeployment: DeploymentSummary | null; selectedDeploymentId: string; offer: CustomerOfferSummary | null
  targetProfileId: string; bindingSlotId: string; operation: CustomerDocumentStorageScope['operation']; selectedProfileId?: string
}): CustomerDocumentStorageScope {
  const { session, deployment, selectedDeployment, offer } = input
  const slot = offer?.bindingSlots.filter(item => item.kind === 'CUSTOMER_S3' && item.id === input.bindingSlotId)
  if (!session?.authenticated || !session.actorId || !session.customerId || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || !deployment || deployment.deploymentId !== input.selectedDeploymentId || !selectedDeployment
    || selectedDeployment.id !== input.selectedDeploymentId || selectedDeployment.binding?.customerId !== session.customerId
    || selectedDeployment.binding.tenantId !== deployment.tenantId || !offer || offer.id !== deployment.offerId
    || offer.fingerprint !== deployment.offerFingerprint || offer.entitlement.customerId !== session.customerId
    || offer.entitlement.status !== 'ACTIVE' || !Number.isSafeInteger(offer.entitlement.revision) || offer.entitlement.revision < 0
    || !offer.targets.some(target => target.id === input.targetProfileId && target.capabilities.includes(input.operation))
    || slot?.length !== 1 || (input.operation === 'BINDING_REPLACE'
      && (!input.selectedProfileId || !slot[0].supportedProfileIds.includes(input.selectedProfileId)))) {
    throw new Error('Current owned deployment, reviewed offer, entitlement and document-storage target are required.')
  }
  return { actorId: session.actorId, authenticationMode: 'SESSION', customerId: session.customerId,
    deploymentId: deployment.deploymentId, tenantId: deployment.tenantId, offerId: offer.id, offerFingerprint: offer.fingerprint,
    entitlementRevision: offer.entitlement.revision, targetProfileId: input.targetProfileId, bindingSlotId: slot[0].id,
    operation: input.operation, ...(input.selectedProfileId ? { selectedProfileId: input.selectedProfileId } : {}) }
}

export async function requireFreshCustomerDocumentStorageScope(scope: CustomerDocumentStorageScope, api: {
  session: () => Promise<PlatformAuthSessionSummary>; offer: (id: string) => Promise<CustomerOfferSummary>
  deployments: () => Promise<CustomerDeploymentSummary[]>
}, stillCurrent: () => boolean) {
  const [session, offer, deployments] = await Promise.all([api.session(), api.offer(scope.offerId), api.deployments()])
  const matches = deployments.filter(item => item.deploymentId === scope.deploymentId)
  const deployment = matches.length === 1 ? matches[0] : null
  const slot = offer.bindingSlots.filter(item => item.kind === 'CUSTOMER_S3' && item.id === scope.bindingSlotId)
  if (!stillCurrent() || !session.authenticated || session.actorId !== scope.actorId || session.customerId !== scope.customerId
    || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION' || !deployment
    || deployment.tenantId !== scope.tenantId || deployment.offerId !== scope.offerId || deployment.offerFingerprint !== scope.offerFingerprint
    || offer.id !== scope.offerId || offer.fingerprint !== scope.offerFingerprint || offer.entitlement.customerId !== scope.customerId
    || offer.entitlement.status !== 'ACTIVE' || offer.entitlement.revision !== scope.entitlementRevision
    || !offer.targets.some(target => target.id === scope.targetProfileId && target.capabilities.includes(scope.operation))
    || slot.length !== 1 || (scope.operation === 'BINDING_REPLACE' && !slot[0].supportedProfileIds.includes(scope.selectedProfileId ?? ''))) {
    throw new Error('Document-storage authority changed. Refresh the current reviewed offer before submitting.')
  }
}
