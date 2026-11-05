import type { HistoryPoint, MonitoringRange, MonitoringServer, ResourceSample } from '../../api/infrastructureMonitoringApi'

export const monitoringQueryRoot = 'infrastructure-monitoring'
export type ConsumerMetric = 'memoryWorkingSetBytes' | 'cpuCoresUsed' | 'restartCount'
export type ResourceRow = { server: MonitoringServer; resource: ResourceSample }
export type InspectorSelection = { serverId: string; resourceId?: string }

export function finite(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function number(value: unknown, digits = 1) {
  return finite(value) ? value.toLocaleString(undefined, { maximumFractionDigits: digits }) : 'Unknown'
}

export function bytes(value: unknown) {
  if (!finite(value) || value < 0) return 'Unknown'
  if (value === 0) return '0 B'
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const unit = Math.min(units.length - 1, Math.floor(Math.log(value) / Math.log(1024)))
  return `${number(value / 1024 ** unit)} ${units[unit]}`
}

export const rate = (value: unknown) => finite(value) ? `${bytes(value)}/s` : 'Unknown'
export const percent = (value: unknown) => finite(value) ? `${number(value)}%` : 'Unknown'
export const cores = (value: unknown) => finite(value) ? `${number(value, 3)} cores` : 'Unknown'
export const timestamp = (value: string | null | undefined) => value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString() : 'Unknown'
export const limit = (value: unknown) => value === 0 ? 'No hard limit' : value === -1 ? 'Unlimited' : bytes(value)

export function age(value: string | null, now: number) {
  const elapsed = value ? now - Date.parse(value) : NaN
  if (!Number.isFinite(elapsed)) return 'Unknown'
  if (elapsed < -5000) return 'Future timestamp'
  const seconds = Math.max(0, Math.floor(elapsed / 1000))
  if (seconds < 60) return `${seconds}s ago`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`
  return `${Math.floor(seconds / 3600)}h ago`
}

export function freshness(server: MonitoringServer, now: number): MonitoringServer['status'] {
  if (server.status === 'NEVER_RECEIVED' || !server.observedAt) return 'NEVER_RECEIVED'
  const observed = Date.parse(server.observedAt)
  if (!Number.isFinite(observed) || observed > now + 5000 || now - observed > 180_000) return 'STALE'
  return server.status
}

export function resourceRows(servers: MonitoringServer[], role: string, search: string, metric: ConsumerMetric): ResourceRow[] {
  const term = search.trim().toLowerCase()
  return servers.flatMap(server => server.resources.map(resource => ({ server, resource })))
    .filter(({ server, resource }) => (!role || resource.role === role) && (!term || [resource.name, resource.image,
      resource.resourceId, resource.deploymentName, resource.deploymentId, resource.targetProfileId,
      resource.role, server.displayName].some(value => value?.toLowerCase().includes(term))))
    .sort((a, b) => {
      const left = a.resource[metric], right = b.resource[metric]
      if (!finite(left)) return finite(right) ? 1 : a.resource.resourceId.localeCompare(b.resource.resourceId)
      if (!finite(right)) return -1
      return right - left || a.resource.resourceId.localeCompare(b.resource.resourceId)
    })
}

export function deploymentPath(resource: Pick<ResourceSample, 'deploymentId' | 'targetProfileId'>) {
  if (!resource.deploymentId) return null
  const query = new URLSearchParams({ deploymentId: resource.deploymentId })
  if (resource.targetProfileId) query.set('targetProfileId', resource.targetProfileId)
  return `/overview?${query}`
}

export function historySeries(points: HistoryPoint[], metric: string, bucketSeconds: number, range: MonitoringRange, now: number, width = 600) {
  const duration = { '1h': 3_600_000, '24h': 86_400_000, '7d': 604_800_000 }[range]
  const start = now - duration
  const samples = points.map(point => ({ point, time: Date.parse(point.observedAt) }))
    .filter(({ time }) => Number.isFinite(time) && time >= start && time <= now).sort((a, b) => a.time - b.time)
  const values = samples.map(({ point }) => point.values[metric]).filter(finite)
  const maxima = samples.map(({ point }) => point.maxValues[metric]).filter(finite)
  const ceiling = Math.max(1, ...values, ...maxima)
  const bucketMs = finite(bucketSeconds) && bucketSeconds > 0 ? bucketSeconds * 1000 : 0
  const x = (time: number) => 64 + (time - start) / duration * Math.max(1, width - 88)
  const y = (value: number) => 117 - value / ceiling * 99
  const paths = (field: 'values' | 'maxValues') => {
    let previous: number | null = null
    return samples.map(({ point, time }) => {
      const value = point[field][metric]
      if (!finite(value)) { previous = null; return '' }
      const connected = previous !== null && bucketMs > 0 && time - previous <= bucketMs * 1.5
      previous = time
      return `${connected ? 'L' : 'M'}${x(time).toFixed(2)},${y(value).toFixed(2)}`
    }).join(' ')
  }
  return { samples, start, end: now, ceiling, mean: values.length ? values.reduce((a, b) => a + b, 0) / values.length : null,
    peak: maxima.length || values.length ? Math.max(...maxima, ...values) : null,
    count: samples.filter(({ point }) => finite(point.values[metric]) || finite(point.maxValues[metric])).length,
    expected: bucketMs > 0 ? Math.ceil(duration / bucketMs) : null,
    meanPath: paths('values'), maxPath: paths('maxValues'), x, y }
}
