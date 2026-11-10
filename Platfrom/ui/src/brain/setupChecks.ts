import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { fetchDeploymentSetupChecks, runDeploymentSetupChecks, type DeploymentSetupChecks, type SetupCheckMode } from '../api/setupChecksApi'

export const setupCheckKey = (deploymentId: string, actorId: string | null, customerId: string | null) =>
  ['deployment-setup-checks', actorId, customerId, deploymentId] as const

export function setupChecksMatchDraft(result: DeploymentSetupChecks | undefined, draftId?: string, revisionNumber?: number) {
  return Boolean(result && draftId !== undefined && revisionNumber !== undefined
    && result.draftId === draftId && result.revisionNumber === revisionNumber)
}

export function useBrainSetupChecks(deploymentId: string, { enabled = true }: { enabled?: boolean } = {}) {
  const client = useQueryClient()
  const { session } = usePlatformAuth()
  const key = setupCheckKey(deploymentId, session?.actorId ?? null, session?.customerId ?? null)
  const mutationKey = [...key, 'run']
  const activityKey = [...key, 'activity']
  const query = useQuery({ queryKey: key,
    queryFn: ({ signal }) => fetchDeploymentSetupChecks(deploymentId, signal),
    enabled: enabled && Boolean(deploymentId), retry: false, staleTime: 30_000,
    refetchOnWindowFocus: false, refetchInterval: enabled ? 30_000 : false })
  const activity = useQuery<{ error: string | null; afterSave: boolean }>({ queryKey: activityKey,
    queryFn: async () => ({ error: null, afterSave: false }), enabled: false,
    staleTime: Infinity })
  const pending = useIsMutating({ mutationKey, exact: true }) > 0
  const mutation = useMutation({ mutationKey, retry: false,
    mutationFn: (request: { deploymentId: string; mode: SetupCheckMode; afterSave: boolean; key: typeof key }) =>
      runDeploymentSetupChecks(request.deploymentId, request.mode),
    onMutate: request => { client.setQueryData([...request.key, 'activity'], { error: null, afterSave: request.afterSave }) },
    onSuccess: async (result, request) => {
      // Account replacement clears the mutation cache. Do not restore another actor's results afterwards.
      if (!client.getMutationCache().getAll().some(item => item.state.variables === request)) return
      await client.cancelQueries({ queryKey: request.key, exact: true })
      if (!client.getMutationCache().getAll().some(item => item.state.variables === request)) return
      client.setQueryData<DeploymentSetupChecks>(request.key, previous =>
        previous && previous.draftId === result.draftId && previous.revisionNumber > result.revisionNumber ? previous : result)
    },
    onError: (_error, request) => {
      if (!client.getMutationCache().getAll().some(item => item.state.variables === request)) return
      client.setQueryData([...request.key, 'activity'], {
        error: 'Setup checks could not complete. Your saved configuration is unchanged. Run Check setup from Updates to try again.',
        afterSave: request.afterSave,
      })
    },
  })
  const run = (mode: SetupCheckMode, afterSave = false, savedDeploymentId = deploymentId) =>
    mutation.mutateAsync({ deploymentId: savedDeploymentId, mode, afterSave,
      key: setupCheckKey(savedDeploymentId, session?.actorId ?? null, session?.customerId ?? null) })
  const runAfterSave = (savedDeploymentId = deploymentId) => { void run('CHANGED', true, savedDeploymentId).catch(() => undefined) }
  return { ...query, run, runAfterSave, pending, runError: activity.data?.error ?? null, afterSave: activity.data?.afterSave ?? false }
}

export type BrainSetupChecksState = ReturnType<typeof useBrainSetupChecks>
