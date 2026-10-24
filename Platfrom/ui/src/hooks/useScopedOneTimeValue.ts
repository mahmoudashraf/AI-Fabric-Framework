import { useEffect, useRef, useState } from 'react'

/** Scope transitions invalidate pending completions, even when navigation later returns to the same scope. */
export function useScopedOneTimeValue<T>(scopeKey: string) {
  const scope = useRef({ key: scopeKey, token: Symbol() })
  if (scope.current.key !== scopeKey) scope.current = { key: scopeKey, token: Symbol() }
  const token = scope.current.token
  const mounted = useRef(true)
  const [stored, setStored] = useState<{ token: symbol; value: T } | null>(null)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  useEffect(() => {
    setStored((previous) => previous?.token === token ? previous : null)
  }, [token])

  return {
    value: stored?.token === token ? stored.value : null,
    capture: () => token,
    isCurrent: (captured: symbol) => mounted.current && scope.current.token === captured,
    publish: (captured: symbol, value: T | null) => {
      if (!mounted.current || scope.current.token !== captured) return false
      setStored(value === null ? null : { token: captured, value })
      return true
    },
  }
}
