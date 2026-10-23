import type { CustomerDeploymentSummary, CustomerExternalIntegrationApprovalOption, CustomerExternalIntegrationProfile,
  CustomerExternalIntegrationScope, CustomerExternalIntegrationSummary, CustomerOfferSummary, DeploymentVersionSummary,
  PlatformAuthSessionSummary } from '../../api/platformApi'

export type ExternalConnectionAuthority = {
  session: PlatformAuthSessionSummary; offer: CustomerOfferSummary; deployment: CustomerDeploymentSummary
  version: DeploymentVersionSummary; scope: CustomerExternalIntegrationScope
}

export function externalConnectionProfile(authority: ExternalConnectionAuthority): CustomerExternalIntegrationProfile {
  const { session, offer, deployment, version, scope } = authority
  const slot = offer.bindingSlots.filter(item => item.id === scope.bindingSlotId && item.kind === 'EXTERNAL_SERVICE')
  const profiles = offer.externalIntegrationProfiles.filter(item => item.id === scope.selectedProfileId
    && item.bindingSlotId === scope.bindingSlotId && item.targetProfileIds.includes(scope.targetProfileId))
  if (!session.authenticated || session.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION'
    || !session.actorId || !session.customerId || offer.entitlement.customerId !== session.customerId
    || offer.entitlement.status !== 'ACTIVE' || !Number.isSafeInteger(offer.entitlement.revision)
    || offer.entitlement.revision < 0 || deployment.offerId !== offer.id || deployment.offerFingerprint !== offer.fingerprint
    || !deployment.tenantId || version.deploymentId !== deployment.deploymentId || version.id !== scope.versionId
    || version.status !== 'PUBLISHED' || !version.configHash || slot.length !== 1 || profiles.length !== 1
    || profiles[0].pluginId !== slot[0].pluginId || !slot[0].supportedProfileIds.includes(scope.selectedProfileId)
    || !offer.targets.some(item => item.id === scope.targetProfileId && item.capabilities.includes('READ'))) {
    throw new Error('The current owned deployment and a published, reviewed connection profile are required.')
  }
  return profiles[0]
}

export function checkedExternalApproval(scope: CustomerExternalIntegrationScope, option: CustomerExternalIntegrationApprovalOption) {
  if (!option.id || option.versionId !== scope.versionId || option.targetProfileId !== scope.targetProfileId
    || option.bindingSlotId !== scope.bindingSlotId || option.selectedProfileId !== scope.selectedProfileId) {
    throw new Error('The approved connection does not match the selected version and target.')
  }
  return option
}

export function checkedExternalBinding(deploymentId: string, scope: CustomerExternalIntegrationScope, binding: CustomerExternalIntegrationSummary) {
  if (!binding.id || binding.deploymentId !== deploymentId || binding.versionId !== scope.versionId
    || binding.targetProfileId !== scope.targetProfileId || binding.bindingSlotId !== scope.bindingSlotId
    || binding.selectedProfileId !== scope.selectedProfileId || !Number.isSafeInteger(binding.revision) || binding.revision < 0) {
    throw new Error('The connection binding does not match the selected deployment, version and target.')
  }
  return binding
}

export async function requireFreshExternalAuthority(authority: ExternalConnectionAuthority, api: {
  session: () => Promise<PlatformAuthSessionSummary>; offer: (id: string) => Promise<CustomerOfferSummary>
  deployments: () => Promise<CustomerDeploymentSummary[]>; versions: (id: string) => Promise<DeploymentVersionSummary[]>
}, stillCurrent: () => boolean, operation: 'READ' | 'BINDING_REPLACE' | 'BINDING_REVOKE') {
  const { offer, deployment, version, session, scope } = authority
  externalConnectionProfile(authority)
  const currentSession = await api.session()
  const currentOffer = await api.offer(offer.id)
  const owned = (await api.deployments()).filter(item => item.deploymentId === deployment.deploymentId)
  const published = (await api.versions(deployment.deploymentId)).filter(item => item.id === version.id)
  if (!stillCurrent() || currentSession.actorId !== session.actorId || currentSession.customerId !== session.customerId
    || currentOffer.fingerprint !== offer.fingerprint || currentOffer.entitlement.revision !== offer.entitlement.revision
    || owned.length !== 1 || owned[0].tenantId !== deployment.tenantId || published.length !== 1
    || published[0].configHash !== version.configHash
    || !currentOffer.targets.some(target => target.id === scope.targetProfileId && target.capabilities.includes(operation))) {
    throw new Error('Connection authority changed. Review the current version and target again.')
  }
  externalConnectionProfile({ session: currentSession, offer: currentOffer, deployment: owned[0], version: published[0], scope })
}
