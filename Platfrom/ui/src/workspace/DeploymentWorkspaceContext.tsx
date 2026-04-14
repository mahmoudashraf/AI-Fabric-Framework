import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  fetchDeployments,
  fetchDeploymentWorkspace,
  fetchPlatformUserPreferences,
  updatePlatformUserPreferences,
  type DeploymentSummary,
  type DeploymentWorkspaceSummary,
  type PlatformUserPreferences,
} from '../api/platformApi'
import type { DeploymentWorkspaceEditorBufferState } from './deploymentWorkspaceLifecycle'

export const DEPLOYMENT_WORKSPACE_PATHS = [
  '/overview',
  '/activity',
  '/actions',
  '/approvals',
  '/access',
  '/knowledge',
  '/marketplace',
  '/poc',
  '/prompts',
  '/providers',
  '/security',
  '/verification',
  '/vectorization',
  '/revisions',
  '/diagnostics',
  '/users',
] as const

type DeploymentWorkspaceContextValue = {
  isScopedPage: boolean
  deployments: DeploymentSummary[]
  deploymentsLoading: boolean
  requestedDeploymentId: string
  unresolvedRequestedDeploymentId: string | null
  selectedDeploymentId: string
  selectedDeploymentSummary: DeploymentSummary | null
  workspace: DeploymentWorkspaceSummary | null
  workspaceLoading: boolean
  editorBufferState: DeploymentWorkspaceEditorBufferState | null
  setSelectedDeploymentId: (deploymentId: string) => void
  setEditorBufferState: (state: DeploymentWorkspaceEditorBufferState | null) => void
  buildWorkspacePath: (pathname: string) => string
}

const DeploymentWorkspaceContext = createContext<DeploymentWorkspaceContextValue | null>(null)

function preferredDeploymentId(
  deployments: DeploymentSummary[],
  preferredId: string | null | undefined,
): string {
  if (preferredId && deployments.some((deployment) => deployment.id === preferredId)) {
    return preferredId
  }
  return deployments.find((deployment) => deployment.status !== 'DRAFT')?.id ?? deployments[0]?.id ?? ''
}

export function isDeploymentWorkspacePath(pathname: string): boolean {
  return DEPLOYMENT_WORKSPACE_PATHS.includes(pathname as (typeof DEPLOYMENT_WORKSPACE_PATHS)[number])
}

export function DeploymentWorkspaceProvider({ children }: { children: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isScopedPage = isDeploymentWorkspacePath(location.pathname)

  const deploymentsQuery = useQuery({
    queryKey: ['deployments'],
    queryFn: fetchDeployments,
    enabled: isScopedPage,
  })

  const deployments = deploymentsQuery.data ?? []
  const preferencesQuery = useQuery({
    queryKey: ['platform-preferences'],
    queryFn: fetchPlatformUserPreferences,
    enabled: isScopedPage,
  })
  const preferences: PlatformUserPreferences | null = preferencesQuery.data ?? null
  const requestedDeploymentId = useMemo(
    () => new URLSearchParams(location.search).get('deploymentId') ?? '',
    [location.search],
  )

  const preferencesLoaded = preferencesQuery.isSuccess || preferencesQuery.isError

  useEffect(() => {
    if (!isScopedPage || deployments.length === 0) {
      return
    }

    if (requestedDeploymentId.length > 0) {
      return
    }

    if (!preferencesLoaded) {
      return
    }

    const preferredId = preferences?.deploymentWorkspace?.lastDeploymentId
    const nextDeploymentId = preferredDeploymentId(deployments, preferredId)

    if (!nextDeploymentId) {
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
  }, [
    deployments,
    isScopedPage,
    location.pathname,
    location.search,
    navigate,
    preferences?.deploymentWorkspace?.lastDeploymentId,
    preferencesLoaded,
    requestedDeploymentId,
  ])

  const selectedDeploymentSummary = useMemo(
    () => deployments.find((deployment) => deployment.id === requestedDeploymentId) ?? null,
    [deployments, requestedDeploymentId],
  )

  const unresolvedRequestedDeploymentId = requestedDeploymentId.length > 0 && !selectedDeploymentSummary
    ? requestedDeploymentId
    : null
  const selectedDeploymentId = selectedDeploymentSummary?.id ?? ''
  const [editorBufferState, setEditorBufferState] = useState<DeploymentWorkspaceEditorBufferState | null>(null)

  const updatePreferencesMutation = useMutation({
    mutationFn: updatePlatformUserPreferences,
    onSuccess: (data) => {
      queryClient.setQueryData(['platform-preferences'], data)
    },
  })

  const lastSavedDeploymentIdRef = useRef('')
  useEffect(() => {
    if (!isScopedPage || selectedDeploymentId.length === 0) {
      return
    }
    if (selectedDeploymentId === lastSavedDeploymentIdRef.current) {
      return
    }
    lastSavedDeploymentIdRef.current = selectedDeploymentId
    updatePreferencesMutation.mutate({
      deploymentWorkspace: {
        lastDeploymentId: selectedDeploymentId,
        lastSection: isDeploymentWorkspacePath(location.pathname) ? location.pathname : null,
      },
    })
  }, [isScopedPage, location.pathname, selectedDeploymentId, updatePreferencesMutation])

  useEffect(() => {
    setEditorBufferState(null)
  }, [location.pathname, selectedDeploymentId])

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
    if (!isDeploymentWorkspacePath(pathname)) {
      return pathname
    }
    const deploymentIdForPath = selectedDeploymentId || unresolvedRequestedDeploymentId || ''
    if (deploymentIdForPath.length === 0) {
      return pathname
    }
    return `${pathname}?deploymentId=${encodeURIComponent(deploymentIdForPath)}`
  }

  const value = useMemo<DeploymentWorkspaceContextValue>(
    () => ({
      isScopedPage,
      deployments,
      deploymentsLoading: deploymentsQuery.isLoading,
      requestedDeploymentId,
      unresolvedRequestedDeploymentId,
      selectedDeploymentId,
      selectedDeploymentSummary,
      workspace: workspaceQuery.data ?? null,
      workspaceLoading: workspaceQuery.isLoading,
      editorBufferState,
      setSelectedDeploymentId,
      setEditorBufferState,
      buildWorkspacePath,
    }),
    [
      deployments,
      deploymentsQuery.isLoading,
      editorBufferState,
      isScopedPage,
      requestedDeploymentId,
      selectedDeploymentId,
      selectedDeploymentSummary,
      unresolvedRequestedDeploymentId,
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
