import { useEffect } from 'react'
import type { DeploymentWorkspaceEditorBufferState } from './deploymentWorkspaceLifecycle'
import { useDeploymentWorkspace } from './DeploymentWorkspaceContext'

export function useDeploymentWorkspaceEditorState(state: DeploymentWorkspaceEditorBufferState | null) {
  const { setEditorBufferState } = useDeploymentWorkspace()

  useEffect(() => {
    setEditorBufferState(state)
    return () => {
      setEditorBufferState(null)
    }
  }, [setEditorBufferState, state])
}
