import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import { isCustomerSession } from '../../auth/customerSession'
import type { SupportExperienceController } from '../../../../../experience-packs/support-experience/src/types'

type Preferences = { requestId: string | null; side: 'left' | 'right'; minimized: boolean }
type View = { kind: 'chat' | 'form' | 'requests' } | { kind: 'request'; id: string }
type SupportContext = {
  customerId: string; actorId: string; ownerKey: string; requestId: string | null
  side: 'left' | 'right'; changeSide(): void; minimized: boolean; setMinimized(value: boolean): void
  diagnosticsOpen: boolean; setDiagnosticsOpen(value: boolean): void
  attachController(controller: SupportExperienceController | null): void
  selectedRequest(id: string | null): void
  openChat(): void; openRequestForm(): void; showRequests(): void; openRequest(id: string): void
}
const Context = createContext<SupportContext | null>(null)
function preferences(key: string): Preferences {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || 'null')
    return { requestId: typeof value?.requestId === 'string' ? value.requestId : null,
      side: value?.side === 'left' ? 'left' : 'right', minimized: value?.minimized === true }
  } catch { return { requestId: null, side: 'right', minimized: false } }
}

/** Presentation preferences only. The reusable pack owns tickets, commands, drafts and chat. */
export function CustomerSupportProvider({ children }: { children: ReactNode }) {
  const auth = usePlatformAuth()
  if (!isCustomerSession(auth.session) || !auth.session?.customerId || !auth.session.actorId) return null
  const scope = `${auth.session?.customerId}:${auth.session?.actorId}:${auth.supabaseSession?.user.id ?? ''}`
  return <ScopedCustomerSupportProvider key={scope} customerId={auth.session.customerId} actorId={auth.session.actorId} subject={auth.supabaseSession?.user.id ?? ''}>{children}</ScopedCustomerSupportProvider>
}

function ScopedCustomerSupportProvider({ children, customerId, actorId, subject }: { children: ReactNode; customerId: string; actorId: string; subject: string }) {
  const key = `loomai:console:support-kit:${customerId}:${actorId}:${subject || actorId}`
  const [settings, setSettings] = useState(() => preferences(key))
  const [diagnosticsOpen, setDiagnosticsOpen] = useState(false)
  const controller = useRef<SupportExperienceController | null>(null)
  const desiredView = useRef<View>(settings.requestId ? { kind: 'request', id: settings.requestId } : { kind: 'chat' })
  useEffect(() => { try { sessionStorage.setItem(key, JSON.stringify(settings)) } catch { /* UI preferences are optional. */ } }, [key, settings])
  const selectedRequest = useCallback((requestId: string | null) => {
    setSettings(value => value.requestId === requestId ? value : { ...value, requestId })
  }, [])
  const applyView = useCallback((next: View) => {
    desiredView.current = next
    const current = controller.current
    if (!current) return
    if (next.kind === 'request') void current.openRequest(next.id)
    else if (next.kind === 'requests') void current.showRequests()
    else if (next.kind === 'form') current.openRequestForm()
    else current.openChat()
  }, [])
  const attachController = useCallback((value: SupportExperienceController | null) => {
    controller.current = value
    if (value) applyView(desiredView.current)
  }, [applyView])
  const show = useCallback((view: View) => {
    setSettings(value => ({ ...value, minimized: false }))
    applyView(view)
  }, [applyView])
  const openChat = useCallback(() => show({ kind: 'chat' }), [show])
  const showRequests = useCallback(() => show({ kind: 'requests' }), [show])
  const openRequestForm = useCallback(() => show({ kind: 'form' }), [show])
  const openRequest = useCallback((id: string) => show({ kind: 'request', id }), [show])
  const setMinimized = useCallback((minimized: boolean) => setSettings(value => value.minimized === minimized ? value : { ...value, minimized }), [])
  return <Context.Provider value={{ customerId, actorId, ownerKey: key, requestId: settings.requestId,
    side: settings.side, changeSide: () => setSettings(value => ({ ...value, side: value.side === 'right' ? 'left' : 'right' })),
    minimized: settings.minimized, setMinimized,
    diagnosticsOpen, setDiagnosticsOpen, attachController, selectedRequest, openChat, openRequestForm, showRequests, openRequest }}>{children}</Context.Provider>
}

export function useCustomerSupport() {
  const value = useContext(Context)
  if (!value) throw new Error('Customer support requires the signed-in customer shell.')
  return value
}
