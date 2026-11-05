import { platformRequest } from './platformApi'

export type MonitoringRange = '1h' | '24h' | '7d'
export type HostMetrics = {
  cpuThreads: number
  cpuUsagePercent: number | null
  load1: number
  load5: number
  load15: number
  memoryTotalBytes: number
  memoryAvailableBytes: number
  swapTotalBytes: number
  swapUsedBytes: number
  swapInBytesPerSecond: number | null
  swapOutBytesPerSecond: number | null
  diskTotalBytes: number
  diskFreeBytes: number
  inodesTotal: number
  inodesFree: number
  networkRxBytesPerSecond: number | null
  networkTxBytesPerSecond: number | null
  cpuStealPercent: number | null
}

export type ResourceSample = {
  resourceId: string
  name: string
  image: string
  providerResourceId: string | null
  providerKind: 'APPLICATION' | 'DATABASE' | 'SHARED' | 'UNMAPPED'
  role: string
  startedAt: string
  state: string
  health: string | null
  memoryWorkingSetBytes: number
  memoryLimitBytes: number
  memoryReservationBytes: number
  memorySwapLimitBytes: number
  cpuCoresUsed: number | null
  cpuQuota: number | null
  cpusetCpus: string
  cpuThrottledPeriodsPercent: number | null
  restartCount: number
  oomKilled: boolean
  deploymentId?: string | null
  deploymentName?: string | null
  targetProfileId?: string | null
}

export type MonitoringServer = {
  serverId: string
  displayName: string
  environment: string
  status: 'FRESH' | 'STALE' | 'NEVER_RECEIVED'
  observedAt: string | null
  receivedAt: string | null
  complete: boolean
  source: string | null
  host: HostMetrics | null
  resources: ResourceSample[]
  warnings: string[]
}

export type MonitoringFleet = {
  sources: { id: string; status: 'AVAILABLE' | 'UNAVAILABLE' | 'DISABLED'; message: string | null }[]
  servers: MonitoringServer[]
}

export type HistoryPoint = {
  observedAt: string
  values: Record<string, number | null>
  maxValues: Record<string, number | null>
}

export type MonitoringHistory = {
  serverId: string
  resourceId: string | null
  range: MonitoringRange
  bucketSeconds: number
  points: HistoryPoint[]
}

const prefix = '/api/admin/infrastructure/monitoring/servers'
const options = (signal?: AbortSignal): RequestInit => ({ method: 'GET', cache: 'no-store', signal })

export function fetchMonitoringServers(signal?: AbortSignal) {
  return platformRequest<MonitoringFleet>(prefix, options(signal))
}

export function fetchMonitoringServer(serverId: string, signal?: AbortSignal) {
  return platformRequest<MonitoringServer>(`${prefix}/${encodeURIComponent(serverId)}`, options(signal))
}

export function fetchMonitoringHistory(serverId: string, range: MonitoringRange, resourceId?: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ range })
  if (resourceId) query.set('resourceId', resourceId)
  return platformRequest<MonitoringHistory>(`${prefix}/${encodeURIComponent(serverId)}/history?${query}`, options(signal))
}
