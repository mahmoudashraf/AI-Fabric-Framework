import type { CustomerDeploymentSummary, CustomerOfferSummary, DeploymentTargetReference,
  DeploymentTargetContextSummary, PlatformAuthSessionSummary } from '../../api/platformApi'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'

export async function prepareCustomerOperatorPlacement(target: DeploymentTargetReference,
  session: PlatformAuthSessionSummary | null, operation: 'INBOX_OPERATOR_ENROLL' | 'INBOX_OPERATOR_ROTATE' | 'INBOX_OPERATOR_DELIVER',
  api: {
    session: () => Promise<PlatformAuthSessionSummary>
    deployments: () => Promise<CustomerDeploymentSummary[]>
    offer: (id: string) => Promise<CustomerOfferSummary>
    target: (target: DeploymentTargetReference) => Promise<DeploymentTargetContextSummary>
  }, stillCurrent: () => boolean) {
  const requireSession = (current: PlatformAuthSessionSummary) => {
    if (!stillCurrent() || !session?.actorId || !session.customerId || !current.authenticated
      || current.role !== 'CUSTOMER_ADMIN' || !isCustomerAuthenticationMode(current.authenticationMode)
      || current.actorId !== session.actorId || current.customerId !== session.customerId) {
      throw new Error('The customer or selected support channel changed. Review it again.')
    }
  }
  requireSession(await api.session())
  const owned = (await api.deployments()).filter(item => item.deploymentId === target.deploymentId)
  if (owned.length !== 1) throw new Error('The selected deployment is not available in this account.')
  const offer = await api.offer(owned[0].offerId)
  const context = await api.target(target)
  if (!stillCurrent() || offer.id !== owned[0].offerId || offer.entitlement.customerId !== session!.customerId || offer.entitlement.status !== 'ACTIVE'
    || offer.fingerprint !== owned[0].offerFingerprint || !owned[0].tenantId
    || context.deploymentId !== target.deploymentId || context.targetProfileId !== target.targetProfileId
    || context.releaseId !== target.releaseId || !context.conversationHandoverConfigured
    || !offer.targets.some(item => item.id === target.targetProfileId && item.capabilities.includes(operation))) {
    throw new Error('The current offer does not authorize this support-channel operation.')
  }
  requireSession(await api.session())
}
