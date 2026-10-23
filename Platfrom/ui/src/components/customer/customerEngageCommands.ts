import type { CustomerDeploymentSummary, CustomerOfferSummary, PlatformConsumerSummary } from '../../api/platformApi'

export type CustomerEngageCommandContext = {
  actorId: string | null | undefined
  customerId: string
  consumers: PlatformConsumerSummary[]
  deployments: CustomerDeploymentSummary[]
  offers: CustomerOfferSummary[]
}

function commandScope(context: CustomerEngageCommandContext, consumerId: string) {
  const consumer = context.consumers.find(item => item.consumerId === consumerId
    && item.customerId === context.customerId && item.status === 'ACTIVE')
  const deployment = context.deployments.find(item => item.deploymentId === consumer?.boundDeploymentId)
  const offer = context.offers.find(item => item.id === deployment?.offerId
    && item.fingerprint === deployment.offerFingerprint && item.entitlement.customerId === context.customerId
    && item.entitlement.status === 'ACTIVE')
  const target = offer?.targets.find(item => item.id === consumer?.boundTargetProfileId)
  if (!context.actorId || !consumer || !deployment || !offer || !target?.capabilities.includes('APP_CONFIGURE')) {
    throw new Error('This app assignment is not available for configuration under the current product offer.')
  }
  return { actorId: context.actorId, customerId: context.customerId, consumerId,
    deploymentId: deployment.deploymentId, offerId: offer.id, fingerprint: offer.fingerprint,
    entitlementRevision: offer.entitlement.revision, targetProfileId: target.id,
    releaseId: consumer.boundReleaseId, bindingRevision: consumer.bindingRevision }
}

/** Refresh the existing placement check only for an explicit app command, never on page load. */
export async function prepareCustomerEngageCommand(
  current: () => CustomerEngageCommandContext | null,
  consumerIds: string | string[],
  probe: (offerId: string, targetProfileId: string) => Promise<unknown>,
) {
  const before = current()
  if (!before) throw new Error('The app configuration view is no longer active.')
  const ids = [...new Set(typeof consumerIds === 'string' ? [consumerIds] : consumerIds)]
  if (ids.length === 0) throw new Error('Select an app assignment before configuring an app.')
  const scopes = ids.map(id => commandScope(before, id))
  const checked = new Set<string>()
  for (const scope of scopes) {
    const key = JSON.stringify([scope.offerId, scope.targetProfileId])
    if (!checked.has(key)) { await probe(scope.offerId, scope.targetProfileId); checked.add(key) }
  }
  const after = current()
  if (!after || JSON.stringify(ids.map(id => commandScope(after, id))) !== JSON.stringify(scopes)) {
    throw new Error('The app assignment or account changed. Review the current app before saving again.')
  }
}
