import type { PlatformConsumerSummary } from '../../api/platformApi'

export function customerEngageSelection(consumers: PlatformConsumerSummary[], customerId: string, requestedId?: string) {
  const eligible = consumers.filter(consumer => consumer.customerId === customerId && consumer.status === 'ACTIVE')
  if (requestedId) return eligible.some(consumer => consumer.consumerId === requestedId)
    ? { consumerId: requestedId, error: null }
    : { consumerId: '', error: 'The requested app assignment is not active in this customer account. Select an available assignment; no replacement was selected.' }
  return { consumerId: eligible.length === 1 ? eligible[0].consumerId : '', error: null }
}
