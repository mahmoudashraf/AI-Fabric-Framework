import { createContext, useContext, useEffect } from 'react'

export type AccountEditorState = { dirty: boolean; busy: boolean; discard: () => void }
export const AccountEditorContext = createContext<(value: AccountEditorState | null) => void>(() => {})

export function useAccountEditor(dirty: boolean, busy: boolean, discard: () => void) {
  const register = useContext(AccountEditorContext)
  useEffect(() => {
    register({ dirty, busy, discard })
    return () => register(null)
  }, [register, dirty, busy, discard])
}
