import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createContext,
  type ReactNode,
  useContext,
  useMemo,
  useRef,
  useState,
} from 'react'
import {
  clearStoredPlatformApiKey,
  fetchPlatformAuthSession,
  getStoredPlatformApiKey,
  loginToPlatform,
  logoutFromPlatform,
  setStoredPlatformApiKey,
  type PlatformAuthSessionSummary,
} from '../api/platformApi'

type PlatformAuthContextValue = {
  apiKey: string
  session: PlatformAuthSessionSummary | null
  isLoading: boolean
  error: Error | null
  signInWithPassword: (email: string, password: string) => Promise<void>
  setApiKey: (value: string) => void
  signOut: () => Promise<void>
  refreshSession: () => Promise<void>
}

const PlatformAuthContext = createContext<PlatformAuthContextValue | null>(null)

export function PlatformAuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [identity, setIdentity] = useState(() => ({ apiKey: getStoredPlatformApiKey(), generation: 0 }))
  const transition = useRef(0)
  const authGeneration = useRef(0)
  const { apiKey, generation } = identity

  const replacePrincipal = (key: string, session: PlatformAuthSessionSummary | null) => {
    const nextGeneration = ++authGeneration.current
    queryClient.clear()
    if (session) queryClient.setQueryData(['platform-auth-session', key, nextGeneration], session)
    // A new query identity reconnects the observer even when the API key remains empty.
    setIdentity({ apiKey: key, generation: nextGeneration })
  }

  const sessionQuery = useQuery({
    queryKey: ['platform-auth-session', apiKey, generation],
    queryFn: fetchPlatformAuthSession,
    retry: false,
  })

  const value = useMemo<PlatformAuthContextValue>(() => ({
    apiKey,
    session: sessionQuery.data ?? null,
    isLoading: sessionQuery.isLoading,
    error: sessionQuery.error instanceof Error ? sessionQuery.error : null,
    signInWithPassword: async (email, password) => {
      const command = ++transition.current
      const session = await loginToPlatform({ email, password })
      if (command !== transition.current) return
      clearStoredPlatformApiKey()
      replacePrincipal('', session)
    },
    setApiKey: (value) => {
      transition.current++
      setStoredPlatformApiKey(value)
      replacePrincipal(getStoredPlatformApiKey(), null)
    },
    signOut: async () => {
      const command = ++transition.current
      const session = await logoutFromPlatform().catch(() => null)
      if (command !== transition.current) return
      clearStoredPlatformApiKey()
      replacePrincipal('', session)
    },
    refreshSession: async () => {
      await queryClient.invalidateQueries({ queryKey: ['platform-auth-session', apiKey, generation], exact: true })
    },
  }), [apiKey, generation, queryClient, sessionQuery.data, sessionQuery.error, sessionQuery.isLoading])

  return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>
}

export function usePlatformAuth() {
  const context = useContext(PlatformAuthContext)
  if (!context) {
    throw new Error('usePlatformAuth must be used inside PlatformAuthProvider')
  }
  return context
}
