import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchPlatformAuthSession, PlatformApiError } from '../api/platformApi'
import { stringifyExactJson } from '../lib/losslessJson'
import { assistantApi } from './api'
import { scopeIdentity } from './scope'
import type { AssistantScope, AssistantSession, SourceInput, TurnInput } from './types'

const errorMessage = (error: unknown) => error instanceof Error ? error.message : String(error)
class EditorRefreshError extends Error {}
export const sessionRunning = (session: AssistantSession | null) => Boolean(session?.activeTurnId || session?.status === 'RUNNING' || session?.turns.some(turn => turn.status === 'RUNNING'))

export function useAssistant(scope: AssistantScope, enabled: boolean, onSaved: () => Promise<void>) {
  const [session, setSession] = useState<AssistantSession | null>(null)
  const [pending, setPending] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const mounted = useRef(false), inFlight = useRef(false), started = useRef(false)
  const saved = useRef(new Set<string>())
  const onSavedRef = useRef(onSaved); onSavedRef.current = onSaved
  const latest = useRef(session); latest.current = session
  const uncertain = useRef<{ input: TurnInput; key: string } | null>(null)
  const lastError = useRef<unknown>(null)
  const commandEpoch = useRef(0)
  const [retryTurn, setRetryTurn] = useState(false)
  const status = useQuery({ queryKey: ['configuration-assistant-status'], queryFn: assistantApi.status, enabled, retry: false })
  const actor = useQuery({ queryKey: ['configuration-assistant-identity'], queryFn: fetchPlatformAuthSession, enabled, retry: false, staleTime: 0 })
  const actorKey = actor.data ? `${actor.data.actorId ?? ''}:${actor.data.customerId ?? ''}` : ''
  const storageKey = `configuration-assistant:${actorKey}:${scopeIdentity(scope)}`

  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const accept = useCallback(async (next: AssistantSession, forceRefresh = false) => {
    if (!mounted.current) return
    // The server owns authorization; this check prevents displaying a stale route's response.
    if (scopeIdentity(next) !== scopeIdentity(scope)) throw new Error('The assistant session does not belong to this editor.')
    latest.current = next
    setSession(next)
    try { sessionStorage.setItem(storageKey, next.id) } catch { /* Storage is optional. */ }
    const receipts = next.turns.filter(turn => turn.status === 'SAVED').map(turn => `${turn.id}:${stringifyExactJson(turn.receipt ?? turn.diff ?? null)}`)
    const unseen = receipts.filter(id => !saved.current.has(id))
    if (unseen.length || forceRefresh) {
      setRefreshing(true)
      try {
        await onSavedRef.current()
        if (mounted.current) { unseen.forEach(id => saved.current.add(id)); setRefreshing(false) }
      } catch (error) {
        const failure = new EditorRefreshError(`Draft changes were saved, but the editor could not be reloaded. Refresh the session to resume editing. ${errorMessage(error)}`)
        if (mounted.current) setError(failure.message)
        throw failure
      }
    }
  }, [scope.resourceKind, scope.resourceId, scope.task, storageKey])

  const command = useCallback(async (run: () => Promise<AssistantSession>, forceRefresh = false) => {
    if (inFlight.current) return false
    inFlight.current = true; commandEpoch.current++; setPending(true); setError(''); lastError.current = null
    try { await accept(await run(), forceRefresh); return true }
    catch (error) { lastError.current = error; if (mounted.current) setError(errorMessage(error)); return false }
    finally { inFlight.current = false; if (mounted.current) setPending(false) }
  }, [accept])

  const start = useCallback(async () => {
    let id: string | null = null
    try { id = sessionStorage.getItem(storageKey) } catch { /* Storage is optional. */ }
    return command(() => id ? assistantApi.get(id) : assistantApi.create(scope))
  }, [storageKey, scope.resourceKind, scope.resourceId, scope.task, command])

  useEffect(() => {
    if (!enabled || !status.data?.enabled || !status.data.ready || !actor.isSuccess || actor.isFetching || started.current) return
    started.current = true
    void start()
  }, [enabled, status.data, actor.isSuccess, actor.isFetching, start])

  const running = sessionRunning(session)
  useEffect(() => {
    if (!running || !session) return
    let stopped = false, timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      const epoch = commandEpoch.current
      try {
        const next = await assistantApi.get(session.id)
        if (stopped) return
        if (epoch === commandEpoch.current && !inFlight.current) {
          await accept(next)
          if (!sessionRunning(next)) { uncertain.current = null; setRetryTurn(false) }
        }
      } catch (error) { if (!stopped) setError(errorMessage(error)) }
      if (!stopped) timer = setTimeout(poll, 1200)
    }
    timer = setTimeout(poll, 800)
    return () => { stopped = true; clearTimeout(timer) }
  }, [running, session?.id, accept])

  const submit = async (input: TurnInput) => {
    if (!latest.current || sessionRunning(latest.current) || inFlight.current || uncertain.current) return false
    const turn = { input, key: crypto.randomUUID() }
    uncertain.current = turn
    const ok = await command(() => assistantApi.turn(latest.current!.id, turn.input, turn.key))
    if (ok) { uncertain.current = null; setRetryTurn(false) }
    else if (lastError.current instanceof EditorRefreshError || lastError.current instanceof PlatformApiError && lastError.current.status >= 400 && lastError.current.status < 500 && lastError.current.status !== 408) {
      uncertain.current = null; setRetryTurn(false)
    } else {
      setRetryTurn(true)
      // A lost HTTP response must not turn into a second billable turn or unlock an active writer.
      if (latest.current) await command(() => assistantApi.get(latest.current!.id))
    }
    return ok
  }
  const retry = async () => {
    const turn = uncertain.current
    if (!turn || !latest.current) return
    if (await command(() => assistantApi.turn(latest.current!.id, turn.input, turn.key))) { uncertain.current = null; setRetryTurn(false) }
  }
  const cancel = () => {
    if (!latest.current) return
    void command(() => assistantApi.cancel(latest.current!.id, latest.current!.activeTurnId)).then(ok => {
      if (ok) { uncertain.current = null; setRetryTurn(false) }
    })
  }
  const remove = async () => {
    if (!session || inFlight.current || running || retryTurn) return
    inFlight.current = true; setPending(true); setError('')
    try {
      await assistantApi.delete(session.id)
      if (!mounted.current) return
      try { sessionStorage.removeItem(storageKey) } catch { /* Storage is optional. */ }
      latest.current = null; setSession(null); saved.current.clear()
    } catch (error) { if (mounted.current) setError(errorMessage(error)) }
    finally { inFlight.current = false; if (mounted.current) setPending(false) }
  }
  return {
    session, status, error: error || (status.error ? errorMessage(status.error) : actor.error ? errorMessage(actor.error) : ''),
    pending, running, busy: pending || running || refreshing || retryTurn, retryTurn, start, submit, retry, cancel, remove,
    newSession: () => command(() => assistantApi.create(scope)),
    refresh: () => session ? command(() => assistantApi.get(session.id)) : start(),
    addSource: async (input: SourceInput): Promise<AssistantSession | null> => {
      if (!session) return null
      let result: AssistantSession | null = null
      const ok = await command(async () => { const next = await assistantApi.source(session.id, input); result = next; return next })
      return ok ? result : null
    },
    reuseSource: async (sessionId: string, sourceId: string): Promise<AssistantSession | null> => {
      if (!session) return null
      let result: AssistantSession | null = null
      const ok = await command(async () => { const next = await assistantApi.reuseSource(session.id, sessionId, [sourceId]); result = next; return next })
      return ok ? result : null
    },
    removeSource: (id: string) => session ? command(() => assistantApi.removeSource(session.id, id)) : Promise.resolve(false),
    proposal: (id: string, action: 'check' | 'apply' | 'undo') => session ? command(() => assistantApi.proposal(session.id, id, action), action === 'undo') : Promise.resolve(false),
  }
}
export type AssistantController = ReturnType<typeof useAssistant>
