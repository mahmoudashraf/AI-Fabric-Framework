import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createClient, type Session, type SupabaseClient } from '@supabase/supabase-js'
import { createContext, type ReactNode, useContext, useEffect, useRef, useState } from 'react'
import { customerAuthReturn } from './customerAuthReturn'
import {
  clearStoredPlatformApiKey, fetchPlatformAuthSession, fetchCustomerAuthConfig, getStoredPlatformApiKey,
  linkCustomerSupabaseIdentity, loginToPlatform, logoutFromPlatform, setStoredPlatformApiKey,
  setPlatformAccessTokenProvider, type CustomerAuthConfig, type PlatformAuthSessionSummary,
} from '../api/platformApi'

type PlatformAuthContextValue = {
  apiKey: string
  session: PlatformAuthSessionSummary | null
  isLoading: boolean
  error: Error | null
  customerConfig: CustomerAuthConfig | null
  supabase: SupabaseClient | null
  supabaseSession: Session | null
  customerAuthLoading: boolean
  customerAuthError: Error | null
  signInWithPassword: (email: string, password: string) => Promise<void>
  signInCustomer: (email: string, password: string) => Promise<void>
  linkCustomer: () => Promise<void>
  setApiKey: (value: string) => void
  signOut: () => Promise<void>
  refreshSession: () => Promise<void>
}

const PlatformAuthContext = createContext<PlatformAuthContextValue | null>(null)
const modeKey = 'loomai-console-auth-mode'

export function PlatformAuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [identity, setIdentity] = useState(() => ({ apiKey: getStoredPlatformApiKey(), generation: 0 }))
  const [supabase, setSupabase] = useState<SupabaseClient | null>(null)
  const [supabaseSession, setSupabaseSession] = useState<Session | null>(null)
  const [customerAuthLoading, setCustomerAuthLoading] = useState(true)
  const [configError, setConfigError] = useState<Error | null>(null)
  const [customerAuthError, setCustomerAuthError] = useState<Error | null>(null)
  const transition = useRef(0)
  const authGeneration = useRef(0)
  const { apiKey, generation } = identity
  const config = useQuery({ queryKey: ['customer-auth-config'], queryFn: fetchCustomerAuthConfig, retry: false, staleTime: Infinity })
  const customerConfig = useRef<CustomerAuthConfig | null>(null)
  if (config.data) customerConfig.current = config.data

  const replacePrincipal = (key: string, session: PlatformAuthSessionSummary | null) => {
    const nextGeneration = ++authGeneration.current
    queryClient.clear()
    if (customerConfig.current) queryClient.setQueryData(['customer-auth-config'], customerConfig.current)
    if (session) queryClient.setQueryData(['platform-auth-session', key, nextGeneration], session)
    // A new query identity reconnects the observer even when the API key remains empty.
    setIdentity({ apiKey: key, generation: nextGeneration })
  }

  useEffect(() => {
    const configured = config.data
    if (config.isError) { setConfigError(new Error('Customer sign-in configuration is unavailable.')); setCustomerAuthLoading(false); return }
    if (!configured) return
    if (!configured.enabled || !configured.projectUrl || !configured.publicKey) { setCustomerAuthLoading(false); return }
    let active = true
    if (window.location.pathname === '/auth/callback' && customerAuthReturn()?.purpose === 'link') {
      sessionStorage.setItem(modeKey, 'link')
    }
    const client = createClient(configured.projectUrl, configured.publicKey, { auth: {
      flowType: 'pkce', detectSessionInUrl: true, persistSession: true, autoRefreshToken: true,
      storageKey: 'loomai-console-supabase',
    } })
    setSupabase(client)
    const accept = (session: Session | null, initial = false) => {
      if (!active) return
      setSupabaseSession(session)
      const mode = sessionStorage.getItem(modeKey)
      if (mode !== 'staff' && mode !== 'link') {
        clearStoredPlatformApiKey()
        setPlatformAccessTokenProvider(session ? async () => {
          const { data, error } = await client.auth.getSession()
          if (error) throw error
          if (!data.session) throw new Error('Customer session expired. Please sign in again.')
          return data.session.access_token
        } : null)
        if (!initial) replacePrincipal('', null)
      }
      setCustomerAuthLoading(false)
    }
    client.auth.getSession().then(({ data, error }) => {
      if (!active) return
      if (error) { setConfigError(error); setCustomerAuthError(error) }
      accept(data.session, true)
    }).catch(error => { if (active) { setConfigError(error); setCustomerAuthError(error); setCustomerAuthLoading(false) } })
    const { data } = client.auth.onAuthStateChange((event, session) => {
      // Never await another SDK operation inside the SDK's storage-lock callback.
      if (event !== 'INITIAL_SESSION') accept(session)
    })
    return () => { active = false; data.subscription.unsubscribe(); client.auth.stopAutoRefresh() }
  }, [config.data, config.isError])

  const sessionQuery = useQuery({
    queryKey: ['platform-auth-session', apiKey, generation],
    queryFn: fetchPlatformAuthSession,
    enabled: !customerAuthLoading,
    retry: false,
  })

  const value: PlatformAuthContextValue = {
    apiKey,
    session: sessionQuery.data ?? null,
    isLoading: customerAuthLoading || sessionQuery.isLoading,
    error: configError ?? (sessionQuery.error instanceof Error ? sessionQuery.error : null),
    customerConfig: config.data ?? customerConfig.current, supabase, supabaseSession, customerAuthLoading, customerAuthError,
    signInWithPassword: async (email, password) => {
      const command = ++transition.current
      sessionStorage.setItem(modeKey, 'staff')
      setPlatformAccessTokenProvider(null)
      const session = await loginToPlatform({ email, password })
      if (command !== transition.current) return
      clearStoredPlatformApiKey()
      replacePrincipal('', session)
    },
    signInCustomer: async (email, password) => {
      if (!supabase) throw new Error('Customer authentication is not configured.')
      ++transition.current
      sessionStorage.setItem(modeKey, 'customer')
      clearStoredPlatformApiKey()
      const { error } = await supabase.auth.signInWithPassword({ email, password })
      if (error) throw error
    },
    linkCustomer: async () => {
      if (!supabase) throw new Error('Customer authentication is not configured.')
      const { data, error } = await supabase.auth.getSession()
      if (error) throw error
      if (!data.session) throw new Error('Sign in to your verified Supabase identity first.')
      await linkCustomerSupabaseIdentity(data.session.access_token)
      await logoutFromPlatform()
      sessionStorage.setItem(modeKey, 'customer')
      clearStoredPlatformApiKey()
      setPlatformAccessTokenProvider(async () => {
        const result = await supabase.auth.getSession()
        if (result.error) throw result.error
        if (!result.data.session) throw new Error('Customer session expired.')
        return result.data.session.access_token
      })
      replacePrincipal('', null)
    },
    setApiKey: (value) => {
      transition.current++
      sessionStorage.setItem(modeKey, 'staff')
      setStoredPlatformApiKey(value)
      replacePrincipal(getStoredPlatformApiKey(), null)
    },
    signOut: async () => {
      const command = ++transition.current
      setPlatformAccessTokenProvider(null)
      clearStoredPlatformApiKey()
      await logoutFromPlatform().catch(() => null)
      const result = await supabase?.auth.signOut({ scope: 'local' })
      if (command !== transition.current) return
      if (result?.error) throw result.error
      sessionStorage.removeItem(modeKey)
      replacePrincipal('', null)
    },
    refreshSession: async () => {
      await queryClient.invalidateQueries({ queryKey: ['platform-auth-session', apiKey, generation], exact: true })
    },
  }

  return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>
}

export function startCustomerIdentityLink() { sessionStorage.setItem(modeKey, 'link') }

export function prepareCustomerIdentity(linking: boolean) {
  sessionStorage.setItem(modeKey, linking ? 'link' : 'customer')
  if (!linking) clearStoredPlatformApiKey()
}

export function usePlatformAuth() {
  const context = useContext(PlatformAuthContext)
  if (!context) {
    throw new Error('usePlatformAuth must be used inside PlatformAuthProvider')
  }
  return context
}
