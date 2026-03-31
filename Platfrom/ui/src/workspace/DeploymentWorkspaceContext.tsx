import { useQuery } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  fetchDeployments,
  fetchDeploymentWorkspace,
  type DeploymentSummary,
  type DeploymentWorkspaceSummary,
} from '../api/platformApi'

export const DEPLOYMENT_WORKSPACE_PATHS = [
  '/actions',
  '/approvals',
  '/access',
  '/knowledge',
  '/poc',
  '/prompts',
  '/providers',
  '/security',
  '/verification',
  '/revisions',
  '/diagnostics',
] as const

type DeploymentWorkspaceContextValue = {
  isScopedPage: boolean
  deployments: DeploymentSummary[]
  deploymentsLoading: boolean
  selectedDeploymentId: string
  selectedDeploymentSummary: DeploymentSummary | null
  workspace: DeploymentWorkspaceSummary | null
  workspaceLoading: boolean
  setSelectedDeploymentId: (deploymentId: string) => void
  buildWorkspacePath: (pathname: string) => string
}

const DeploymentWorkspaceContext = createContext<DeploymentWorkspaceContextValue | null>(null)

function preferredDeploymentId(deployments: DeploymentSummary[]): string {
  return deployments.find((deployment) => deployment.status !== 'DRAFT')?.id ?? deployments[0]?.id ?? ''
}

export function isDeploymentWorkspacePath(pathname: string): boolean {
  return DEPLOYMENT_WORKSPACE_PATHS.includes(pathname as (typeof DEPLOYMENT_WORKSPACE_PATHS)[number])
}

export function DeploymentWorkspaceProvider({ children }: { children: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()
  const isScopedPage = isDeploymentWorkspacePath(location.pathname)

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
    enabled: isScopedPage,
  })

  const deployments = deploymentsQuery.data ?? []
  const requestedDeploymentId = useMemo(
    () => new URLSearchParams(location.search).get('deploymentId') ?? '',
    [location.search],
  )

  useEffect(() => {
    if (!isScopedPage || deployments.length === 0) {
      return
    }

    const nextDeploymentId = deployments.some((deployment) => deployment.id === requestedDeploymentId)
      ? requestedDeploymentId
      : preferredDeploymentId(deployments)

    if (!nextDeploymentId || nextDeploymentId === requestedDeploymentId) {
      return
    }

    const nextParams = new URLSearchParams(location.search)
    nextParams.set('deploymentId', nextDeploymentId)
    navigate(
      {
        pathname: location.pathname,
        search: `?${nextParams.toString()}`,
      },
      { replace: true },
    )
  }, [deployments, isScopedPage, location.pathname, location.search, navigate, requestedDeploymentId])

  const selectedDeploymentSummary = useMemo(
    () => deployments.find((deployment) => deployment.id === requestedDeploymentId) ?? null,
    [deployments, requestedDeploymentId],
  )

  const selectedDeploymentId = selectedDeploymentSummary?.id ?? ''

  const workspaceQuery = useQuery({
    queryKey: ['deployment-workspace', selectedDeploymentId],
    queryFn: () => fetchDeploymentWorkspace(selectedDeploymentId),
    enabled: isScopedPage && selectedDeploymentId.length > 0,
  })

  const setSelectedDeploymentId = (deploymentId: string) => {
    const nextParams = new URLSearchParams(location.search)
    if (deploymentId.trim().length === 0) {
      nextParams.delete('deploymentId')
    } else {
      nextParams.set('deploymentId', deploymentId)
    }
    const nextSearch = nextParams.toString()
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch.length > 0 ? `?${nextSearch}` : '',
      },
      { replace: false },
    )
  }

  const buildWorkspacePath = (pathname: string) => {
    if (!isDeploymentWorkspacePath(pathname) || selectedDeploymentId.length === 0) {
      return pathname
    }
    return `${pathname}?deploymentId=${encodeURIComponent(selectedDeploymentId)}`
  }

  const value = useMemo<DeploymentWorkspaceContextValue>(
    () => ({
      isScopedPage,
      deployments,
      deploymentsLoading: deploymentsQuery.isLoading,
      selectedDeploymentId,
      selectedDeploymentSummary,
      workspace: workspaceQuery.data ?? null,
      workspaceLoading: workspaceQuery.isLoading,
      setSelectedDeploymentId,
      buildWorkspacePath,
    }),
    [
      deployments,
      deploymentsQuery.isLoading,
      isScopedPage,
      selectedDeploymentId,
      selectedDeploymentSummary,
      workspaceQuery.data,
      workspaceQuery.isLoading,
    ],
  )

  return (
    <DeploymentWorkspaceContext.Provider value={value}>
      {children}
    </DeploymentWorkspaceContext.Provider>
  )
}

export function useDeploymentWorkspace() {
  const context = useContext(DeploymentWorkspaceContext)
  if (!context) {
    throw new Error('useDeploymentWorkspace must be used within DeploymentWorkspaceProvider.')
  }
  return context
}
