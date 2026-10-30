import { useQuery } from '@tanstack/react-query'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { listSupportStaffIntake } from '../../api/supportApi'

export function useSupportStaffAccess(enabled = true, page = 0, size = 25) {
  const auth = usePlatformAuth()
  const subject = auth.supabaseSession?.user.id
  return useQuery({
    queryKey: ['support-staff-intake', subject, page, size],
    enabled: enabled && Boolean(subject && auth.supabase), retry: false, refetchOnWindowFocus: false, gcTime: 0,
    queryFn: async () => {
      const { data, error } = await auth.supabase!.auth.getSession()
      if (error || !data.session || data.session.user.id !== subject) throw new Error('Sign in again to view team intake.')
      return listSupportStaffIntake(data.session.access_token, page, size)
    },
  })
}
