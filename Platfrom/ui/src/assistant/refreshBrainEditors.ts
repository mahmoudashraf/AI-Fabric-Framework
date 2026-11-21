import type { QueryClient } from '@tanstack/react-query'

export async function refreshBrainEditors(client: QueryClient, brainId: string, specialistIdentity: string) {
  const owned = (key: readonly unknown[]) => key.includes(brainId)
    || key[0] === 'customer-specialist-assistance' && key[1] === specialistIdentity
  await client.invalidateQueries({ predicate: query => owned(query.queryKey), refetchType: 'none' })
  // Live delivery telemetry is not an editable draft dependency. Its own view retains errors.
  await client.refetchQueries({ type: 'active', predicate: query => owned(query.queryKey)
    && query.queryKey[0] !== 'deployment-webhooks-overview' }, { throwOnError: true })
}
