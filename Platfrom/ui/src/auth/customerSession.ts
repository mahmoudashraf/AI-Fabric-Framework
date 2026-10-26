import type { PlatformAuthSessionSummary } from '../api/platformApi'

export function isCustomerAuthenticationMode(mode: string | null | undefined): mode is 'SESSION' | 'SUPABASE' {
  return mode === 'SESSION' || mode === 'SUPABASE'
}

export function isCustomerSession(session: PlatformAuthSessionSummary | null | undefined): session is PlatformAuthSessionSummary & {
  authenticated: true; actorId: string; customerId: string; role: 'CUSTOMER_ADMIN'; authenticationMode: 'SESSION' | 'SUPABASE'
} {
  return Boolean(session?.authenticated && session.actorId && session.customerId
    && session.role === 'CUSTOMER_ADMIN' && isCustomerAuthenticationMode(session.authenticationMode))
}
