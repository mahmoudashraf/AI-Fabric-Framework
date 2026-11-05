import { Box, Button, Chip, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography } from '@mui/material'
import type { MonitoringServer } from '../../api/infrastructureMonitoringApi'
import { age, bytes, freshness, number, percent, rate, timestamp } from './monitoringModels'

export function Freshness({ server, now }: { server: MonitoringServer; now: number }) {
  const status = freshness(server, now)
  return <Stack direction="row" gap={0.5} flexWrap="wrap">
    <Chip size="small" label={{ FRESH: 'Fresh', STALE: 'Stale', NEVER_RECEIVED: 'Never received' }[status]}
      color={status === 'FRESH' ? 'success' : status === 'STALE' ? 'warning' : 'default'} variant="outlined" />
    {!server.complete && server.observedAt && <Chip label="Partial" size="small" color="warning" variant="outlined" />}
  </Stack>
}

export function ServerOverview({ servers, now, onSelect }: { servers: MonitoringServer[]; now: number; onSelect: (serverId: string) => void }) {
  return <TableContainer component={Box} sx={{ overflowX: 'auto' }} tabIndex={0} aria-label="Server overview scroll area">
    <Table size="small" sx={{ minWidth: 960 }} aria-label="Server overview">
      <TableHead><TableRow>
        {['Server', 'Server CPU / threads', 'Available RAM / total', 'Swap in / out', 'Free disk / total', 'Containers', 'Freshness'].map(label => <TableCell key={label}>{label}</TableCell>)}
      </TableRow></TableHead>
      <TableBody>{servers.map(server => <TableRow key={server.serverId} hover sx={{ '& td': { py: 1.5, verticalAlign: 'top' } }}>
        <TableCell><Button sx={{ p: 0, minWidth: 0, textAlign: 'left', justifyContent: 'flex-start', overflowWrap: 'anywhere' }}
          onClick={() => onSelect(server.serverId)} aria-label={`Inspect server ${server.displayName}`}>{server.displayName}</Button>
          <Typography variant="caption" component="p" color="text.secondary">{server.environment}</Typography></TableCell>
        <TableCell>{percent(server.host?.cpuUsagePercent)} / {number(server.host?.cpuThreads, 0)}
          <Typography variant="caption" component="p" color="text.secondary">Load 1m: {number(server.host?.load1)}</Typography></TableCell>
        <TableCell>{bytes(server.host?.memoryAvailableBytes)} / {bytes(server.host?.memoryTotalBytes)}</TableCell>
        <TableCell>{rate(server.host?.swapInBytesPerSecond)} / {rate(server.host?.swapOutBytesPerSecond)}
          <Typography variant="caption" component="p" color="text.secondary">Occupied: {bytes(server.host?.swapUsedBytes)}</Typography></TableCell>
        <TableCell>{bytes(server.host?.diskFreeBytes)} / {bytes(server.host?.diskTotalBytes)}</TableCell>
        <TableCell>{server.observedAt ? server.resources.length : 'Unknown'}
          {server.observedAt && <Typography variant="caption" component="p" color="text.secondary">
            {server.resources.filter(resource => resource.state.toLowerCase() === 'running').length} running
            {' / '}{server.resources.filter(resource => resource.health?.toLowerCase() === 'unhealthy').length} unhealthy
          </Typography>}</TableCell>
        <TableCell><Freshness server={server} now={now} /><Tooltip title={`Observed: ${timestamp(server.observedAt)}; received: ${timestamp(server.receivedAt)}`}>
          <Typography component="p" variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
            Observed {age(server.observedAt, now)}<br />Received {age(server.receivedAt, now)}
          </Typography></Tooltip></TableCell>
      </TableRow>)}</TableBody>
    </Table>
  </TableContainer>
}
