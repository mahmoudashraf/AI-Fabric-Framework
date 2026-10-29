import type { CustomerDeploymentSummary, PlatformAuthSessionSummary, PlatformConsumerSummary } from '../../api/platformApi'
import { isCustomerAuthenticationMode } from '../../auth/customerSession'

export type CustomerEngageCommandContext = {
  actorId: string | null | undefined
  customerId: string
  consumers: PlatformConsumerSummary[]
  deployments: CustomerDeploymentSummary[]
}

function commandScope(context: CustomerEngageCommandContext, consumerId: string, requireRelease: boolean) {
  const consumer = context.consumers.find(item => item.consumerId === consumerId
    && item.customerId === context.customerId && item.status === 'ACTIVE')
  const deployment = context.deployments.find(item => item.deploymentId === consumer?.boundDeploymentId)
  if (!context.actorId || !consumer || !deployment || !consumer.boundTargetProfileId || (requireRelease && !consumer.boundReleaseId)) {
    throw new Error(requireRelease ? 'Connect a verified runtime release before activating this app.'
      : 'Connect a deployment and environment belonging to this customer account.')
  }
  // Current ownership and assignment scope the command; the server authorizes the actual target and operation.
  return { actorId: context.actorId, customerId: context.customerId, consumerId,
    deploymentId: deployment.deploymentId, targetProfileId: consumer.boundTargetProfileId,
    releaseId: consumer.boundReleaseId, bindingRevision: consumer.bindingRevision }
}

/** Recheck the authenticated account and exact assignment for an explicit app command. */
export async function prepareCustomerEngageCommand(
  current: () => CustomerEngageCommandContext | null,
  consumerIds: string | string[],
  session: () => Promise<PlatformAuthSessionSummary>,
  options: { requireRelease?: boolean } = {},
) {
  const before = current()
  if (!before) throw new Error('The app configuration view is no longer active.')
  const ids = [...new Set(typeof consumerIds === 'string' ? [consumerIds] : consumerIds)]
  if (ids.length === 0) throw new Error('Select an app assignment before configuring an app.')
  const scopes = ids.map(id => commandScope(before, id, options.requireRelease === true))
  const account = await session()
  if (!account.authenticated || account.role !== 'CUSTOMER_ADMIN' || !isCustomerAuthenticationMode(account.authenticationMode)
    || account.actorId !== before.actorId || account.customerId !== before.customerId) {
    throw new Error('The app account changed. Sign in to the current customer account before continuing.')
  }
  const after = current()
  if (!after || JSON.stringify(ids.map(id => commandScope(after, id, options.requireRelease === true))) !== JSON.stringify(scopes)) {
    throw new Error('The app assignment or account changed. Review the current app before saving again.')
  }
}
