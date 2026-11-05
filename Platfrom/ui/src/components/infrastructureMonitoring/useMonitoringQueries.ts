import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchMonitoringHistory, fetchMonitoringServer, fetchMonitoringServers, type MonitoringRange } from '../../api/infrastructureMonitoringApi'
import { PlatformApiError } from '../../api/platformApi'
import { monitoringQueryRoot } from './monitoringModels'

export function useMonitoringVisibility() {
  const [visible, setVisible] = useState(() => document.visibilityState === 'visible')
  useEffect(() => {
    const update = () => setVisible(document.visibilityState === 'visible')
    document.addEventListener('visibilitychange', update)
    return () => document.removeEventListener('visibilitychange', update)
  }, [])
  return visible
}

export function useMonitoringCacheBoundary(scope: string) {
  const client = useQueryClient()
  const [denied, setDenied] = useState(false)
  useEffect(() => {
    const queryKey = [monitoringQueryRoot, scope]
    const purge = () => {
      void client.cancelQueries({ queryKey })
      client.removeQueries({ queryKey })
    }
    // Loss of read authorization must not leave a last-known privileged snapshot visible.
    const unsubscribe = client.getQueryCache().subscribe(event => {
      if (event.type !== 'updated' || event.action.type !== 'error'
        || event.query.queryKey[0] !== monitoringQueryRoot || event.query.queryKey[1] !== scope) return
      const error = event.query.state.error
      if (error instanceof PlatformApiError && (error.status === 401 || error.status === 403)) {
        setDenied(true)
        purge()
      }
    })
    return () => { unsubscribe(); purge() }
  }, [client, scope])
  return denied
}

const polling = (visible: boolean) => ({
  enabled: visible,
  staleTime: 20_000,
  gcTime: 0,
  retry: false as const,
  refetchInterval: visible ? 30_000 : false as const,
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: true,
})

export function useMonitoringFleet(scope: string, visible: boolean) {
  return useQuery({ queryKey: [monitoringQueryRoot, scope, 'fleet'],
    queryFn: ({ signal }) => fetchMonitoringServers(signal), ...polling(visible) })
}

export function useMonitoringServer(scope: string, serverId: string | undefined, visible: boolean) {
  return useQuery({ queryKey: [monitoringQueryRoot, scope, 'server', serverId],
    queryFn: ({ signal }) => fetchMonitoringServer(serverId!, signal), ...polling(visible), enabled: visible && !!serverId })
}

export function useMonitoringHistory(scope: string, serverId: string | undefined, range: MonitoringRange, visible: boolean, resourceId?: string) {
  return useQuery({ queryKey: [monitoringQueryRoot, scope, 'history', serverId, resourceId ?? null, range],
    queryFn: ({ signal }) => fetchMonitoringHistory(serverId!, range, resourceId, signal),
    ...polling(visible), enabled: visible && !!serverId })
}
