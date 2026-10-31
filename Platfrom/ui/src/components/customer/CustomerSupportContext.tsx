import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { createSupportCase, startSupportConversation, type CreateSupportCase, type SupportCase } from '../../api/supportApi'
import type { GenericWorkspaceState } from '../../../../../experience-packs/generic-workspace-experience/src/types'

type Preferences = { caseId: string | null; side: 'left' | 'right'; minimized: boolean }
type SupportContext = {
  customerId: string; actorId: string; caseId: string | null; selectCase(id: string | null): void
  side: 'left' | 'right'; changeSide(): void; minimized: boolean; setMinimized(value: boolean): void
  requestOpen: boolean; setRequestOpen(value: boolean): void
  saving: boolean; intakeError: string | null; savedCase: SupportCase | null
  submitIntake(body: CreateSupportCase, navigateToCase: boolean): Promise<boolean>
  pendingIntake: boolean; pendingBody: CreateSupportCase | null; workspaceState: GenericWorkspaceState | null
  setWorkspaceState(value: GenericWorkspaceState | null): void
}
const Context = createContext<SupportContext | null>(null)
const defaults: Preferences = { caseId: null, side: 'right', minimized: false }
function preferences(key: string): Preferences {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || 'null')
    return { caseId: typeof value?.caseId === 'string' ? value.caseId : null,
      side: value?.side === 'left' ? 'left' : 'right', minimized: value?.minimized === true }
  } catch { return defaults }
}

/** One host per signed-in customer; route navigation never owns or resets its iframe. */
export function CustomerSupportProvider({ children }: { children: ReactNode }) {
  const auth = usePlatformAuth()
  const customerId = auth.session!.customerId!
  const actorId = auth.session!.actorId!
  const key = `loomai:console:support:${customerId}:${actorId}:${auth.supabaseSession?.user.id ?? actorId}`
  const [settings, setSettings] = useState(() => preferences(key))
  const selectedCase = useRef(settings.caseId)
  const [requestOpen, setRequestOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [intakeError, setIntakeError] = useState<string | null>(null)
  const [savedCase, setSavedCase] = useState<SupportCase | null>(null)
  const [workspaceState, setWorkspaceState] = useState<GenericWorkspaceState | null>(null)
  const pending = useRef<{ key: string; body: CreateSupportCase } | null>(null)
  const busy = useRef(false)
  const active = useRef(true)
  const cache = useQueryClient()
  const navigate = useNavigate()
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  useEffect(() => { try { sessionStorage.setItem(key, JSON.stringify(settings)) } catch { /* Storage is optional; authorization remains server-owned. */ } }, [key, settings])
  const selectCase = useCallback((id: string | null) => {
    if (selectedCase.current !== id) setWorkspaceState(null)
    selectedCase.current = id
    setSettings(value => value.caseId === id ? value : { ...value, caseId: id })
  }, [])
  const submitIntake = async (body: CreateSupportCase, navigateToCase: boolean) => {
    if (busy.current || !body.summary.trim()) return false
    busy.current = true; setSaving(true); setIntakeError(null)
    if (!pending.current) pending.current = { key: crypto.randomUUID(), body: structuredClone(body) }
    let created: SupportCase | null = null
    let deliveryError: string | null = null
    try {
      created = await createSupportCase(pending.current.body, pending.current.key)
      if (!active.current) return false
      setSavedCase(created)
      cache.setQueryData(['support-case', customerId, actorId, created.id], created)
      const delivery = await startSupportConversation(created.id)
      if (!active.current) return false
      cache.setQueryData(['support-case', customerId, actorId, created.id], delivery.supportCase)
      if (delivery.runtimeStatus >= 400) deliveryError = `The runtime returned ${delivery.runtimeStatus}. Your request is saved; no replacement message was sent.`
    } catch (error) {
      if (!active.current) return false
      if (!created) { setIntakeError(error instanceof Error ? error.message : 'Your request could not be saved.'); return false }
      deliveryError = error instanceof Error ? error.message : 'The initial AI response could not be confirmed.'
    } finally {
      busy.current = false
      if (active.current) {
        setSaving(false)
        if (created) {
          selectCase(created.id)
          setSettings(value => ({ ...value, minimized: false }))
          setRequestOpen(false); setSavedCase(null); pending.current = null
          setIntakeError(deliveryError)
          void cache.invalidateQueries({ queryKey: ['support-cases', customerId, actorId] })
          void cache.invalidateQueries({ queryKey: ['support-case', customerId, actorId, created.id] })
          if (navigateToCase) navigate(`/support/${encodeURIComponent(created.id)}`)
        }
      }
    }
    return Boolean(created)
  }
  return <Context.Provider value={{ customerId, actorId, caseId: settings.caseId, selectCase,
    side: settings.side, changeSide: () => setSettings(value => ({ ...value, side: value.side === 'right' ? 'left' : 'right' })),
    minimized: settings.minimized, setMinimized: minimized => setSettings(value => ({ ...value, minimized })),
    requestOpen, setRequestOpen, saving, intakeError, savedCase, submitIntake,
    pendingIntake: Boolean(pending.current), pendingBody: pending.current?.body ?? null, workspaceState, setWorkspaceState }}>{children}</Context.Provider>
}

export function useCustomerSupport() {
  const value = useContext(Context)
  if (!value) throw new Error('Customer support requires the signed-in customer shell.')
  return value
}
