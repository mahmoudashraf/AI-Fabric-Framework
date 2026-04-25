import { createClient, type Provider, type Session, type SupabaseClient, type User } from '@supabase/supabase-js'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { createPartnerApiClient, type PartnerApiClient } from './apiClient'

interface SupabaseAuthContextValue {
  client: SupabaseClient | null
  api: PartnerApiClient
  session: Session | null
  user: User | null
  loading: boolean
  configured: boolean
  signInWithProvider: (provider: Provider) => Promise<void>
  signInWithEmail: (email: string) => Promise<void>
  signOut: () => Promise<void>
  getSession: () => Promise<Session | null>
}

const SupabaseAuthContext = createContext<SupabaseAuthContextValue | null>(null)

function authRedirectUrl(path = '/auth/callback') {
  return `${window.location.origin}${path}`
}

function createSupabaseClient() {
  const url = import.meta.env.VITE_SUPABASE_URL
  const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY
  if (!url || !anonKey) {
    return null
  }
  return createClient(url, anonKey, {
    auth: {
      detectSessionInUrl: true,
      persistSession: true,
      autoRefreshToken: true,
    },
  })
}

export function SupabaseProvider({ children }: { children: ReactNode }) {
  const client = useMemo(createSupabaseClient, [])
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  const getSession = useCallback(async () => {
    if (!client) {
      return null
    }
    const result = await client.auth.getSession()
    return result.data.session
  }, [client])

  useEffect(() => {
    let active = true
    if (!client) {
      setLoading(false)
      return undefined
    }
    client.auth.getSession().then(({ data }) => {
      if (active) {
        setSession(data.session)
        setLoading(false)
      }
    })
    const { data } = client.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
      setLoading(false)
    })
    return () => {
      active = false
      data.subscription.unsubscribe()
    }
  }, [client])

  const api = useMemo(() => createPartnerApiClient(getSession), [getSession])

  const value = useMemo<SupabaseAuthContextValue>(
    () => ({
      client,
      api,
      session,
      user: session?.user ?? null,
      loading,
      configured: Boolean(client),
      signInWithProvider: async (provider) => {
        if (!client) {
          throw new Error('Supabase is not configured.')
        }
        const { error } = await client.auth.signInWithOAuth({
          provider,
          options: { redirectTo: authRedirectUrl() },
        })
        if (error) {
          throw error
        }
      },
      signInWithEmail: async (email) => {
        if (!client) {
          throw new Error('Supabase is not configured.')
        }
        const { error } = await client.auth.signInWithOtp({
          email,
          options: { emailRedirectTo: authRedirectUrl() },
        })
        if (error) {
          throw error
        }
      },
      signOut: async () => {
        if (client) {
          await client.auth.signOut()
        }
      },
      getSession,
    }),
    [api, client, getSession, loading, session],
  )

  return <SupabaseAuthContext.Provider value={value}>{children}</SupabaseAuthContext.Provider>
}

export function useSupabaseAuth() {
  const context = useContext(SupabaseAuthContext)
  if (!context) {
    throw new Error('useSupabaseAuth must be used inside SupabaseProvider.')
  }
  return context
}
