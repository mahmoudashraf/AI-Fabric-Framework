import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { consoleExperienceKey, developerLandingHost, explicitConsoleExperience, readConsoleExperience, type ConsoleExperience } from './developerEntry'

const pendingKey = 'loomai-console-pending-experience'
const changedEvent = 'loomai-console-experience-changed'
const Context = createContext({ developer: false, selectExperience: (_value: ConsoleExperience) => {} })

function pendingExperience(): ConsoleExperience | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(pendingKey) ?? 'null')
    return value && typeof value.createdAt === 'number' && value.createdAt <= Date.now()
      && Date.now() - value.createdAt < 3_600_000 && ['DEVELOPER', 'CUSTOMER'].includes(value.mode) ? value.mode : null
  } catch { return null }
}

/** A remembered view, never an account role or resource authorization policy. */
export function DeveloperExperienceProvider({ children }: { children: ReactNode }) {
  const auth = usePlatformAuth(), location = useLocation(), navigate = useNavigate()
  const [memory, setMemory] = useState<Record<string, ConsoleExperience>>({})
  const [, refresh] = useState(0)
  const customer = isCustomerSession(auth.session)
  const scope = customer ? consoleExperienceKey(auth.session!.customerId!, auth.session!.actorId!) : 'guest'
  const explicit = explicitConsoleExperience(location.search)
    ?? (location.pathname === '/developers' || location.pathname === '/developers/'
      || location.pathname === '/' && developerLandingHost(window.location.hostname) ? 'DEVELOPER' : null)
  const pending = !auth.session?.authenticated || customer ? pendingExperience() : null
  let stored: ConsoleExperience | null = null
  try { stored = customer ? readConsoleExperience(window.localStorage, scope) : null } catch { /* Storage can be disabled by the browser. */ }
  const mode = explicit ?? pending ?? stored ?? memory[scope] ?? 'CUSTOMER'

  useEffect(() => {
    const update = () => refresh(value => value + 1)
    window.addEventListener('storage', update)
    window.addEventListener(changedEvent, update)
    return () => { window.removeEventListener('storage', update); window.removeEventListener(changedEvent, update) }
  }, [])

  useEffect(() => {
    if (auth.isLoading) return
    if (customer) {
      const next = explicit ?? pending
      if (next) {
        try { localStorage.setItem(scope, next); sessionStorage.removeItem(pendingKey) } catch { /* The current view still works without storage. */ }
        setMemory(values => values[scope] === next ? values : { ...values, [scope]: next })
      }
    } else if (auth.session?.authenticated) {
      try { sessionStorage.removeItem(pendingKey) } catch { /* A guest preference grants no staff authority. */ }
    } else if (explicit) {
      try { sessionStorage.setItem(pendingKey, JSON.stringify({ mode: explicit, createdAt: Date.now() })) } catch { /* URL intent remains available. */ }
    }
  }, [auth.isLoading, auth.session?.authenticated, customer, explicit, pending, scope])

  const selectExperience = (next: ConsoleExperience) => {
    try {
      if (customer) { localStorage.setItem(scope, next); sessionStorage.removeItem(pendingKey) }
      else sessionStorage.setItem(pendingKey, JSON.stringify({ mode: next, createdAt: Date.now() }))
    } catch { /* Keep the current tab functional when browser storage is disabled. */ }
    setMemory(values => ({ ...values, [scope]: next }))
    const search = new URLSearchParams(location.search)
    search.set('entryPoint', next)
    navigate({ pathname: location.pathname, search: `?${search}`, hash: location.hash }, { replace: true })
    window.dispatchEvent(new Event(changedEvent))
  }

  return <Context.Provider value={{ developer: mode === 'DEVELOPER', selectExperience }}>{children}</Context.Provider>
}

export const useDeveloperExperience = () => useContext(Context)
