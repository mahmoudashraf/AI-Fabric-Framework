import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchPlatformAuthSession, PlatformApiError } from '../api/platformApi'
import { assistantApi } from './api'
import { scopeIdentity } from './scope'
import { goalOpen, savedGoalOperations, sessionRunning } from './goalPresentation'
import type { AssistantGoalCommand, AssistantScope, AssistantSession, SourceInput, TurnInput } from './types'

const errorMessage = (error: unknown) => error instanceof Error ? error.message : String(error)
class EditorRefreshError extends Error {}
const definitiveFailure = (error: unknown) => error instanceof EditorRefreshError || error instanceof PlatformApiError && error.status >= 400 && error.status < 500 && error.status !== 408
export { sessionRunning } from './goalPresentation'

export function useAssistant(scope: AssistantScope, enabled: boolean, onSaved: () => Promise<void>) {
  const [session, setSession] = useState<AssistantSession | null>(null)
  const [pending, setPending] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [awaitingGoal, setAwaitingGoal] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const mounted = useRef(false), inFlight = useRef(false), started = useRef(false)
  const saved = useRef(new Set<string>())
  const savedGoalCounts = useRef(new Map<string, number>())
  const onSavedRef = useRef(onSaved); onSavedRef.current = onSaved
  const latest = useRef(session); latest.current = session
  const uncertain = useRef<{ input: TurnInput; key: string; sessionId: string; goalId?: string } | null>(null)
  const pinnedSessionId = useRef<string | null>(null)
  const lastError = useRef<unknown>(null)
  const commandEpoch = useRef(0)
  const [retryTurn, setRetryTurn] = useState(false)
  const [earlierSessionId, setEarlierSessionId] = useState<string | null>(null)
  const status = useQuery({ queryKey: ['configuration-assistant-status'], queryFn: assistantApi.status, enabled, retry: false })
  const actor = useQuery({ queryKey: ['configuration-assistant-identity'], queryFn: fetchPlatformAuthSession, enabled, retry: false, staleTime: 0 })
  const actorKey = actor.data ? `${actor.data.actorId ?? ''}:${actor.data.customerId ?? ''}` : ''
  const storageKey = `configuration-assistant:${actorKey}:${scopeIdentity(scope)}`
  const goalStorageKey = `configuration-assistant:${actorKey}:active-goal`
  const pendingGoalKey = `${goalStorageKey}:pending`

  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const accept = useCallback(async (next: AssistantSession, forceRefresh = false) => {
    // The server owns authorization; this check prevents displaying a stale route's response.
    if (scopeIdentity(next) !== scopeIdentity(scope) && next.id !== pinnedSessionId.current) throw new Error('The assistant session does not belong to this editor.')
    let awaiting = false
    try {
      const key = `configuration-assistant:${actorKey}:${scopeIdentity(next)}`
      sessionStorage.setItem(key, next.id)
      awaiting = !next.goal && sessionStorage.getItem(pendingGoalKey) === next.id
      if (next.goal && sessionStorage.getItem(pendingGoalKey) === next.id) sessionStorage.removeItem(pendingGoalKey)
      if (goalOpen(next.goal) || awaiting) sessionStorage.setItem(goalStorageKey, next.id)
      else if (sessionStorage.getItem(goalStorageKey) === next.id) sessionStorage.removeItem(goalStorageKey)
    } catch { /* Storage is optional. */ }
    if (!mounted.current) return
    latest.current = next
    setSession(next); setAwaitingGoal(awaiting)
    const receipts = next.engine === 'LEGACY' ? [] : next.turns.flatMap(turn => turn.operations?.length
      ? turn.operations.filter(operation => operation.status === 'EXECUTED').map(operation => `${turn.id}:${operation.operationId}`)
      : turn.status === 'SAVED' ? [`${turn.id}:saved`] : [])
    receipts.push(...savedGoalOperations(next).map(operation => `goal:${next.goal!.id}:${operation.operationId}`))
    const unseen = receipts.filter(id => !saved.current.has(id))
    const goalCount = next.goal?.operationCount
    const additionalGoalSaves = next.engine !== 'LEGACY' && next.goal && typeof goalCount === 'number' && Number.isSafeInteger(goalCount)
      && goalCount > (savedGoalCounts.current.get(next.goal.id) ?? 0)
    if ((unseen.length || additionalGoalSaves || forceRefresh) && next.resourceKind === scope.resourceKind && next.resourceId === scope.resourceId) {
      setRefreshing(true)
      try {
        await onSavedRef.current()
        if (mounted.current) {
          unseen.forEach(id => saved.current.add(id))
          if (additionalGoalSaves && next.goal && goalCount !== undefined) savedGoalCounts.current.set(next.goal.id, goalCount)
        }
      } catch (error) {
        const failure = new EditorRefreshError(`Draft changes were saved, but the editor could not be reloaded. Refresh the session to load the saved values. ${errorMessage(error)}`)
        if (mounted.current) setError(failure.message)
        throw failure
      } finally {
        if (mounted.current) setRefreshing(false)
      }
    }
  }, [scope.resourceKind, scope.resourceId, scope.task, actorKey, goalStorageKey, pendingGoalKey])

  const command = useCallback(async (run: () => Promise<AssistantSession>, forceRefresh = false) => {
    if (inFlight.current) return false
    inFlight.current = true; commandEpoch.current++; setPending(true); setError(''); lastError.current = null
    try { await accept(await run(), forceRefresh); return true }
    catch (error) { lastError.current = error; if (mounted.current) setError(errorMessage(error)); return false }
    finally { inFlight.current = false; if (mounted.current) setPending(false) }
  }, [accept])

  const start = useCallback(async () => {
    let id: string | null = null, goalId: string | null = null, pendingId: string | null = null
    try { id = sessionStorage.getItem(storageKey); goalId = sessionStorage.getItem(goalStorageKey); pendingId = sessionStorage.getItem(pendingGoalKey); setEarlierSessionId(sessionStorage.getItem(`${storageKey}:earlier`)) } catch { /* Storage is optional. */ }
    return command(async () => {
      if (goalId) {
        try {
          const active = await assistantApi.get(goalId)
          if (goalOpen(active.goal) || !active.goal && pendingId === active.id) { pinnedSessionId.current = active.id; return active }
        } catch (error) {
          if (!(error instanceof PlatformApiError) || ![403, 404, 410].includes(error.status)) throw error
        }
        try { sessionStorage.removeItem(goalStorageKey) } catch { /* Storage is optional. */ }
      }
      return id ? assistantApi.get(id) : assistantApi.create(scope)
    })
  }, [storageKey, goalStorageKey, pendingGoalKey, scope.resourceKind, scope.resourceId, scope.task, command])

  useEffect(() => {
    if (!enabled || !status.data?.enabled || !status.data.ready || !actor.isSuccess || actor.isFetching || started.current) return
    started.current = true
    void start()
  }, [enabled, status.data, actor.isSuccess, actor.isFetching, start])

  const running = sessionRunning(session) || awaitingGoal
  const openGoal = goalOpen(session?.goal)
  useEffect(() => {
    if ((!running && !openGoal) || !session) return
    let stopped = false, timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      const epoch = commandEpoch.current
      try {
        const next = await assistantApi.get(session.id)
        if (stopped) return
        if (epoch === commandEpoch.current && !inFlight.current) {
          if (awaitingGoal && !next.goal) {
            let stillPending = true
            try { stillPending = sessionStorage.getItem(pendingGoalKey) === next.id } catch { /* Storage is optional. */ }
            if (!stillPending) { setAwaitingGoal(false); pinnedSessionId.current = null; await start(); return }
          }
          await accept(next)
          if (!sessionRunning(next) && !uncertain.current?.goalId) { uncertain.current = null; setRetryTurn(false) }
        }
      } catch (error) { if (!stopped) setError(errorMessage(error)) }
      if (!stopped) timer = setTimeout(poll, running ? 1200 : 5000)
    }
    timer = setTimeout(poll, 800)
    return () => { stopped = true; clearTimeout(timer) }
  }, [running, openGoal, awaitingGoal, pendingGoalKey, session?.id, accept, start])

  const sendTurn = async (sessionId: string, input: TurnInput, key: string) => {
    const next = await assistantApi.turn(sessionId, input, key)
    // Older bounded servers can acknowledge a turn without a native goal projection.
    if (input.executionMode === 'AUTONOMOUS' && !next.goal) {
      if (mounted.current) setNotice('The server acknowledged this turn without an autonomous goal. Only the recorded turn status is available.')
      try {
        if (sessionStorage.getItem(pendingGoalKey) === sessionId) sessionStorage.removeItem(pendingGoalKey)
        if (sessionStorage.getItem(goalStorageKey) === sessionId) sessionStorage.removeItem(goalStorageKey)
      } catch { /* Storage is optional. */ }
    }
    return next
  }
  const submit = async (input: TurnInput) => {
    if (!latest.current || sessionRunning(latest.current) || goalOpen(latest.current.goal) || inFlight.current || uncertain.current) return false
    setNotice('')
    const turn = { input, key: crypto.randomUUID(), sessionId: latest.current.id }
    uncertain.current = turn
    if (input.executionMode === 'AUTONOMOUS') {
      // Navigation may occur before admission returns its durable goal handle.
      try { sessionStorage.setItem(goalStorageKey, turn.sessionId); sessionStorage.setItem(pendingGoalKey, turn.sessionId) } catch { /* Storage is optional. */ }
    }
    const ok = await command(() => sendTurn(turn.sessionId, turn.input, turn.key))
    if (ok) { uncertain.current = null; setRetryTurn(false) }
    else if (definitiveFailure(lastError.current)) {
      uncertain.current = null; setRetryTurn(false)
      if (!goalOpen(latest.current?.goal)) {
        try {
          if (sessionStorage.getItem(pendingGoalKey) === turn.sessionId) sessionStorage.removeItem(pendingGoalKey)
          if (sessionStorage.getItem(goalStorageKey) === turn.sessionId) sessionStorage.removeItem(goalStorageKey)
        } catch { /* Storage is optional. */ }
        if (mounted.current) setAwaitingGoal(false)
      }
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
    const ok = await command(() => turn.goalId
      ? assistantApi.directive(turn.sessionId, turn.goalId, { query: turn.input.query }, turn.key)
      : sendTurn(turn.sessionId, turn.input, turn.key))
    if (ok || definitiveFailure(lastError.current)) { uncertain.current = null; setRetryTurn(false) }
  }
  const directive = async (query: string) => {
    const current = latest.current
    if (!current?.goal || !goalOpen(current.goal) || inFlight.current || uncertain.current) return false
    const request = { input: { query, previewOnly: true, sourceIds: [], operationIds: [] }, key: crypto.randomUUID(), sessionId: current.id, goalId: current.goal.id }
    uncertain.current = request
    const ok = await command(() => assistantApi.directive(request.sessionId, request.goalId, { query }, request.key))
    if (ok || definitiveFailure(lastError.current)) {
      uncertain.current = null; setRetryTurn(false)
    } else {
      setRetryTurn(true)
      await command(() => assistantApi.get(request.sessionId))
    }
    return ok
  }
  const controlGoal = (action: AssistantGoalCommand) => {
    const current = latest.current
    if (!current?.goal) return
    void command(() => assistantApi.goalControl(current.id, current.goal!.id, action)).then(ok => {
      if (ok && action === 'CANCEL') { uncertain.current = null; setRetryTurn(false) }
    })
  }
  const cancel = () => {
    if (!latest.current) return
    if (latest.current.goal && goalOpen(latest.current.goal)) { controlGoal('CANCEL'); return }
    void command(() => assistantApi.cancel(latest.current!.id, latest.current!.activeTurnId)).then(ok => {
      if (ok) { uncertain.current = null; setRetryTurn(false) }
    })
  }
  const remove = async () => {
    if (!session || inFlight.current || running || openGoal || retryTurn) return
    inFlight.current = true; setPending(true); setError('')
    try {
      await assistantApi.delete(session.id)
      if (!mounted.current) return
      try { sessionStorage.removeItem(`configuration-assistant:${actorKey}:${scopeIdentity(session)}`) } catch { /* Storage is optional. */ }
      if (session.id === earlierSessionId) {
        setEarlierSessionId(null)
        try { sessionStorage.removeItem(`${storageKey}:earlier`) } catch { /* Storage is optional. */ }
      }
      latest.current = null; setSession(null); saved.current.clear(); savedGoalCounts.current.clear()
    } catch (error) { if (mounted.current) setError(errorMessage(error)) }
    finally { inFlight.current = false; if (mounted.current) setPending(false) }
  }
  return {
    session, status, notice, error: error || (status.error ? errorMessage(status.error) : actor.error ? errorMessage(actor.error) : ''),
    pending, running, openGoal, awaitingGoal, busy: pending || running || refreshing || retryTurn, composerBusy: pending || refreshing || retryTurn || running && !openGoal,
    retryTurn, start, submit, directive, controlGoal, retry, cancel, remove,
    newSession: () => {
      if (latest.current?.engine === 'LEGACY') {
        const id = latest.current.id
        setEarlierSessionId(id)
        try { sessionStorage.setItem(`${storageKey}:earlier`, id) } catch { /* Storage is optional. */ }
      }
      return command(() => assistantApi.create(scope))
    },
    returnToPage: () => {
      if (goalOpen(latest.current?.goal)) return Promise.resolve(false)
      return command(async () => {
        let id: string | null = null
        try { id = sessionStorage.getItem(storageKey) } catch { /* Storage is optional. */ }
        const next = id ? await assistantApi.get(id) : await assistantApi.create(scope)
        pinnedSessionId.current = null
        return next
      })
    },
    earlierSessionId,
    earlierHistory: () => earlierSessionId ? command(() => assistantApi.get(earlierSessionId)) : Promise.resolve(false),
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
