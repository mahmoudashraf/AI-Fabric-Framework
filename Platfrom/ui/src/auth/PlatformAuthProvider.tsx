import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createContext,
  type ReactNode,
  useContext,
  useMemo,
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
  const [apiKey, setApiKeyState] = useState(() => getStoredPlatformApiKey())

  const sessionQuery = useQuery({
    queryKey: ['platform-auth-session', apiKey],
    queryFn: fetchPlatformAuthSession,
    retry: false,
  })

  const value = useMemo<PlatformAuthContextValue>(() => ({
    apiKey,
    session: sessionQuery.data ?? null,
    isLoading: sessionQuery.isLoading,
    error: sessionQuery.error instanceof Error ? sessionQuery.error : null,
    signInWithPassword: async (email, password) => {
      clearStoredPlatformApiKey()
      setApiKeyState('')
      const session = await loginToPlatform({ email, password })
      queryClient.setQueryData(['platform-auth-session', ''], session)
      await queryClient.invalidateQueries({ queryKey: ['platform-auth-session'] })
    },
    setApiKey: (value) => {
      setStoredPlatformApiKey(value)
      setApiKeyState(getStoredPlatformApiKey())
      void queryClient.invalidateQueries({ queryKey: ['platform-auth-session'] })
    },
    signOut: async () => {
      const session = await logoutFromPlatform().catch(() => null)
      clearStoredPlatformApiKey()
      setApiKeyState('')
      if (session) {
        queryClient.setQueryData(['platform-auth-session', ''], session)
      }
      await queryClient.invalidateQueries({ queryKey: ['platform-auth-session'] })
    },
    refreshSession: async () => {
      await queryClient.invalidateQueries({ queryKey: ['platform-auth-session'] })
    },
  }), [apiKey, queryClient, sessionQuery.data, sessionQuery.error, sessionQuery.isLoading])

  return <PlatformAuthContext.Provider value={value}>{children}</PlatformAuthContext.Provider>
}

export function usePlatformAuth() {
  const context = useContext(PlatformAuthContext)
  if (!context) {
    throw new Error('usePlatformAuth must be used inside PlatformAuthProvider')
  }
  return context
}
