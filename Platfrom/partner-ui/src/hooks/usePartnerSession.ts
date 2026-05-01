import { useQuery } from '@tanstack/react-query'
import { getPartnerSession } from '../api/session'
import { useSupabaseAuth } from '../auth/SupabaseProvider'

export function usePartnerSession() {
  const { api, session } = useSupabaseAuth()
  return useQuery({
    queryKey: ['partner-session', session?.user.id],
    queryFn: () => getPartnerSession(api),
    enabled: Boolean(session),
    staleTime: 30_000,
  })
}
