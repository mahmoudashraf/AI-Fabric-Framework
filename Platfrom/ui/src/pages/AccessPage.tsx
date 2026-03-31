import { Navigate } from 'react-router-dom'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

export function AccessPage() {
  const workspace = useDeploymentWorkspace()
  return <Navigate to={workspace.buildWorkspacePath('/users')} replace />
}
