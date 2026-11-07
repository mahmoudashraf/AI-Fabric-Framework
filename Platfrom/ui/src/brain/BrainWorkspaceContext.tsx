import { createContext, useContext, useEffect, useRef } from 'react'
import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary, DeploymentTargetReference } from '../api/platformApi'

export type BrainEditorState = {
  dirty: boolean
  busy?: boolean
  canSave?: boolean
  save?: () => void | Promise<unknown>
  discard?: () => void
  message?: string
  error?: string | null
  primaryLabel?: string
  secondaryLabel?: string
}

export const BrainWorkspaceContext = createContext<{
  brainId: string
  targetProfileId: string
  targetProfile: DeploymentTargetProfileSummary | null
  release: DeploymentReleaseSummary | null
  target: DeploymentTargetReference | null
  href: (section?: string) => string
} | null>(null)

export const BrainEditorContext = createContext<((state: BrainEditorState | null) => void) | null>(null)

export function useBrainWorkspace() {
  const value = useContext(BrainWorkspaceContext)
  if (!value) throw new Error('A Smart Brain workspace is required.')
  return value
}

export function useOptionalBrainWorkspace() { return useContext(BrainWorkspaceContext) }

export function useBrainEditor(state: BrainEditorState | null) {
  const register = useContext(BrainEditorContext)
  const latest = useRef(state)
  latest.current = state
  const active = state !== null
  // Register stable callbacks while their implementation follows the current editor buffer.
  useEffect(() => {
    if (!register || !active) return
    register({ dirty: state!.dirty, busy: state!.busy, canSave: state!.canSave,
      message: state!.message, error: state!.error, primaryLabel: state!.primaryLabel,
      secondaryLabel: state!.secondaryLabel,
      save: state!.save ? () => latest.current?.save?.() : undefined,
      discard: state!.discard ? () => latest.current?.discard?.() : undefined })
    return () => register(null)
  }, [register, active, state?.dirty, state?.busy, state?.canSave, state?.message, state?.error,
    state?.primaryLabel, state?.secondaryLabel, Boolean(state?.save), Boolean(state?.discard)])
}
