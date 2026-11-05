import { deploymentTerm } from '../../deploymentTerminology'
import CloseRoundedIcon from '@mui/icons-material/CloseRounded'
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import { Alert, Box, Divider, Drawer, IconButton, LinearProgress, Link, Stack, Tooltip, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import type { ReactNode } from 'react'
import type { MonitoringRange, MonitoringServer, ResourceSample } from '../../api/infrastructureMonitoringApi'
import { age, bytes, cores, deploymentPath, finite, limit, number, percent, rate, timestamp, type InspectorSelection } from './monitoringModels'
import { MonitoringHistory } from './MonitoringHistory'
import { Freshness } from './ServerOverview'
import { useMonitoringServer } from './useMonitoringQueries'

function Measurements({ entries }: { entries: [string, ReactNode][] }) {
  return <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.2fr)', columnGap: 2, rowGap: 1 }}>
    {entries.map(([label, value]) => <Box key={label} sx={{ display: 'contents' }}>
      <Typography component="dt" variant="body2" color="text.secondary">{label}</Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: 'anywhere' }}>{value}</Typography>
    </Box>)}
  </Box>
}

function Footprints({ resources }: { resources: ResourceSample[] }) {
  const groups = new Map<string, ResourceSample[]>()
  for (const resource of resources) {
    const key = resource.deploymentId ? JSON.stringify([resource.deploymentId, resource.targetProfileId ?? null])
      : resource.providerKind === 'SHARED' ? 'Shared infrastructure' : 'Unmapped'
    groups.set(key, [...(groups.get(key) ?? []), resource])
  }
  return <Stack spacing={1}>
    <Typography variant="subtitle2">{deploymentTerm.singular} / target footprints</Typography>
    {[...groups.entries()].map(([key, items]) => {
      const first = items[0], path = deploymentPath(first)
      const memory = items.every(item => finite(item.memoryWorkingSetBytes)) ? items.reduce((sum, item) => sum + item.memoryWorkingSetBytes, 0) : null
      const cpu = items.every(item => finite(item.cpuCoresUsed)) ? items.reduce((sum, item) => sum + item.cpuCoresUsed!, 0) : null
      return <Box key={key} sx={{ borderBottom: 1, borderColor: 'divider', pb: 1, overflowWrap: 'anywhere' }}>
        <Typography variant="body2">{path ? <Link component={RouterLink} to={path}>{first.deploymentName || first.deploymentId}</Link> : key}</Typography>
        {first.deploymentId && <Typography variant="caption" color="text.secondary">Target: {first.targetProfileId || 'Unmapped'}</Typography>}
        <Typography variant="body2">{items.length} containers / {bytes(memory)} / {cores(cpu)}</Typography>
      </Box>
    })}
    {resources.length === 0 && <Typography variant="body2" color="text.secondary">No resource samples.</Typography>}
  </Stack>
}

function ServerMeasurements({ server, now }: { server: MonitoringServer; now: number }) {
  const h = server.host
  const knownTargets = new Set(server.resources.filter(resource => resource.deploymentId && resource.targetProfileId)
    .map(resource => JSON.stringify([resource.deploymentId, resource.targetProfileId])))
  return <>
    <Measurements entries={[
      ['Server ID', server.serverId], ['Environment', server.environment], ['Source', server.source || 'Unknown'],
      ['Observed', timestamp(server.observedAt)], ['Received', timestamp(server.receivedAt)],
      ['Sample age', age(server.observedAt, now)], ['Completeness', server.complete ? 'Complete' : 'Partial'],
      ['Server CPU', percent(h?.cpuUsagePercent)], ['CPU threads', number(h?.cpuThreads, 0)],
      ['Load average 1 / 5 / 15m', [h?.load1, h?.load5, h?.load15].map(value => number(value)).join(' / ')],
      ['CPU steal', percent(h?.cpuStealPercent)], ['Available / total RAM', `${bytes(h?.memoryAvailableBytes)} / ${bytes(h?.memoryTotalBytes)}`],
      ['Occupied / total swap', `${bytes(h?.swapUsedBytes)} / ${bytes(h?.swapTotalBytes)}`],
      ['Swap in / out', `${rate(h?.swapInBytesPerSecond)} / ${rate(h?.swapOutBytesPerSecond)}`],
      ['Free / total disk', `${bytes(h?.diskFreeBytes)} / ${bytes(h?.diskTotalBytes)}`],
      ['Free / total inodes', `${number(h?.inodesFree, 0)} / ${number(h?.inodesTotal, 0)}`],
      ['Network receive / transmit', `${rate(h?.networkRxBytesPerSecond)} / ${rate(h?.networkTxBytesPerSecond)}`],
      ['Running / stopped containers', server.observedAt ? `${server.resources.filter(r => r.state.toLowerCase() === 'running').length} / ${server.resources.filter(r => ['exited', 'dead', 'stopped', 'created'].includes(r.state.toLowerCase())).length}` : 'Unknown'],
      ['Unhealthy containers', server.observedAt ? number(server.resources.filter(r => r.health?.toLowerCase() === 'unhealthy').length, 0) : 'Unknown'],
      ['Restart count (current containers)', server.observedAt ? number(server.resources.reduce((sum, r) => sum + r.restartCount, 0), 0) : 'Unknown'],
      ['Containers with OOM evidence', server.observedAt ? number(server.resources.filter(r => r.oomKilled).length, 0) : 'Unknown'],
      [`Mapped ${deploymentTerm.lowercase} / target pairs`, server.observedAt ? number(knownTargets.size, 0) : 'Unknown'],
    ]} />
    <Divider />
    <Footprints resources={server.resources} />
  </>
}

function ResourceMeasurements({ resource }: { resource: ResourceSample }) {
  const path = deploymentPath(resource)
  return <Measurements entries={[
    [deploymentTerm.singular, path ? <Link component={RouterLink} to={path}>{resource.deploymentName || resource.deploymentId}<OpenInNewRoundedIcon sx={{ ml: 0.5, fontSize: 14, verticalAlign: 'middle' }} /></Link> : resource.providerKind === 'SHARED' ? 'Shared infrastructure' : 'Unmapped'],
    ['Target profile', resource.targetProfileId || 'Unmapped'], ['Role', resource.role || 'Unknown'],
    ['Container ID', resource.resourceId], ['Provider resource ID', resource.providerResourceId || 'Unmapped'],
    ['Provider kind', resource.providerKind], ['Image', resource.image || 'Unknown'], ['Started', timestamp(resource.startedAt)],
    ['State', resource.state || 'Unknown'], ['Health', resource.health || 'Unknown'],
    ['Working-set memory', bytes(resource.memoryWorkingSetBytes)], ['Hard memory limit', limit(resource.memoryLimitBytes)],
    ['Memory reservation', bytes(resource.memoryReservationBytes)], ['Memory + swap limit', resource.memorySwapLimitBytes === -1 ? 'Unlimited' : bytes(resource.memorySwapLimitBytes)],
    ['CPU (core-equivalents)', cores(resource.cpuCoresUsed)], ['CPU quota', resource.cpuQuota === 0 ? 'No quota' : cores(resource.cpuQuota)],
    ['CPU affinity (cpuset)', resource.cpusetCpus || 'No explicit affinity'], ['Quota-throttled periods', percent(resource.cpuThrottledPeriodsPercent)],
    ['Restart count', number(resource.restartCount, 0)], ['OOM-killed evidence', resource.oomKilled ? 'Yes' : 'No'],
  ]} />
}

export function MonitoringInspector({ selection, fleetServer, scope, range, visible, now, onClose }: {
  selection: InspectorSelection | null; fleetServer?: MonitoringServer; scope: string; range: MonitoringRange; visible: boolean; now: number; onClose: () => void
}) {
  const query = useMonitoringServer(scope, selection?.serverId, visible)
  const server = query.data ?? fleetServer
  const resource = selection?.resourceId ? server?.resources.find(item => item.resourceId === selection.resourceId) : undefined
  return <Drawer anchor="right" open={!!selection} onClose={onClose} PaperProps={{
    role: 'dialog', 'aria-modal': true, 'aria-labelledby': 'monitoring-inspector-title',
    sx: { width: { xs: '100%', sm: 560 }, maxWidth: '100%', backgroundImage: 'none' },
  }}>
    <Stack spacing={2} sx={{ p: { xs: 2, sm: 3 }, minWidth: 0 }}>
      <Stack direction="row" alignItems="flex-start" gap={1}>
        <Box sx={{ flex: 1, minWidth: 0 }}><Typography id="monitoring-inspector-title" variant="h6" sx={{ overflowWrap: 'anywhere' }}>
          {selection?.resourceId ? resource?.name || 'Resource inspector' : server?.displayName || 'Server inspector'}</Typography>
          <Typography variant="body2" color="text.secondary">{selection?.resourceId ? 'Resource' : 'Server'} / {server?.environment || 'Unknown environment'}</Typography></Box>
        <Tooltip title="Refresh stored server data"><span><IconButton aria-label="Refresh stored server data" disabled={query.isFetching || !visible}
          onClick={() => void query.refetch()}><RefreshRoundedIcon /></IconButton></span></Tooltip>
        <Tooltip title="Close inspector"><IconButton aria-label="Close inspector" onClick={onClose}><CloseRoundedIcon /></IconButton></Tooltip>
      </Stack>
      {query.isFetching && <LinearProgress aria-label="Loading server inspector" />}
      {query.isError && <Alert severity="warning">Server details unavailable.{server ? ' Last returned observations are shown.' : ''}</Alert>}
      {!query.data && fleetServer && <Typography variant="caption" color="text.secondary">Fleet snapshot / {timestamp(fleetServer.observedAt)}</Typography>}
      {server && <>
        <Freshness server={server} now={now} />
        <Typography variant="caption" color="text.secondary">Observed {timestamp(server.observedAt)} / Received {timestamp(server.receivedAt)}</Typography>
        {server.warnings.map((warning, index) => <Alert key={`${index}-${warning}`} severity="warning" sx={{ overflowWrap: 'anywhere' }}>{warning}</Alert>)}
        {selection?.resourceId ? resource ? <ResourceMeasurements resource={resource} /> : <Alert severity="info">This container is absent from the latest returned sample.</Alert>
          : <ServerMeasurements server={server} now={now} />}
      </>}
      <Divider /><Typography variant="subtitle2">History / {range}</Typography>
      {selection && <MonitoringHistory scope={scope} serverId={selection.serverId} resourceId={selection.resourceId} range={range} visible={visible} now={now} />}
    </Stack>
  </Drawer>
}
