import MemoryRoundedIcon from '@mui/icons-material/MemoryRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import SpeedRoundedIcon from '@mui/icons-material/SpeedRounded'
import { Alert, Box, Chip, Divider, IconButton, InputAdornment, LinearProgress, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import type { MonitoringRange } from '../api/infrastructureMonitoringApi'
import { age, monitoringQueryRoot, resourceRows, timestamp, type ConsumerMetric, type InspectorSelection } from '../components/infrastructureMonitoring/monitoringModels'
import { MonitoringHistory } from '../components/infrastructureMonitoring/MonitoringHistory'
import { MonitoringInspector } from '../components/infrastructureMonitoring/MonitoringInspector'
import { ResourceConsumers } from '../components/infrastructureMonitoring/ResourceConsumers'
import { ServerOverview } from '../components/infrastructureMonitoring/ServerOverview'
import { useMonitoringCacheBoundary, useMonitoringFleet, useMonitoringVisibility } from '../components/infrastructureMonitoring/useMonitoringQueries'

export function InfrastructureMonitoringPage() {
  const auth = usePlatformAuth()
  const identity = JSON.stringify([auth.session?.actorId, auth.session?.role, auth.session?.authenticationMode, auth.session?.customerId])
  const previous = useRef({ identity, apiKey: auth.apiKey, generation: 0 })
  if (previous.current.identity !== identity || previous.current.apiKey !== auth.apiKey) {
    previous.current = { identity, apiKey: auth.apiKey, generation: previous.current.generation + 1 }
  }
  const scope = `${identity}:${previous.current.generation}`
  const allowed = !auth.isLoading && !auth.error && auth.session?.authenticated === true
    && auth.session.role === 'PLATFORM_ADMIN' && !auth.session.customerId
  if (!allowed) return <Alert severity={auth.isLoading ? 'info' : 'error'}>
    {auth.isLoading ? 'Checking administrator access...' : 'Platform administrator access is required.'}
  </Alert>
  return <AdminMonitoring key={scope} scope={scope} />
}

function AdminMonitoring({ scope }: { scope: string }) {
  const visible = useMonitoringVisibility()
  const denied = useMonitoringCacheBoundary(scope)
  const queryClient = useQueryClient()
  const fleet = useMonitoringFleet(scope, visible && !denied)
  const [environment, setEnvironment] = useState('')
  const [range, setRange] = useState<MonitoringRange>('1h')
  const [role, setRole] = useState('')
  const [search, setSearch] = useState('')
  const [metric, setMetric] = useState<ConsumerMetric>('memoryWorkingSetBytes')
  const [trendServerId, setTrendServerId] = useState('')
  const [selection, setSelection] = useState<InspectorSelection | null>(null)
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (!visible) return
    setNow(Date.now())
    const timer = window.setInterval(() => setNow(Date.now()), 15_000)
    return () => window.clearInterval(timer)
  }, [visible])
  const allServers = fleet.data?.servers ?? []
  const environments = [...new Set([...allServers.map(server => server.environment), ...(environment ? [environment] : [])])].sort()
  const servers = useMemo(() => allServers.filter(server => !environment || server.environment === environment), [allServers, environment])
  const roles = [...new Set([...servers.flatMap(server => server.resources.map(resource => resource.role)), ...(role ? [role] : [])])].filter(Boolean).sort()
  const rows = useMemo(() => resourceRows(servers, role, search, metric), [servers, role, search, metric])
  const trendServer = servers.find(server => server.serverId === trendServerId) ?? servers[0]
  const received = allServers.map(server => server.receivedAt).filter((value): value is string => !!value && Number.isFinite(Date.parse(value))).sort((a, b) => Date.parse(b) - Date.parse(a))[0]
  const refresh = () => {
    setNow(Date.now())
    void queryClient.invalidateQueries({ queryKey: [monitoringQueryRoot, scope], refetchType: 'active' })
  }
  const busy = fleet.isFetching
  if (denied) return <Alert severity="error">Monitoring read access was denied. Platform administrator authentication is required.</Alert>
  return <Stack spacing={2.5} sx={{ minWidth: 0, '& .MuiTypography-root': { letterSpacing: 0 } }}>
    <Stack direction="row" alignItems="center" gap={1.5} flexWrap="wrap">
      <Typography component="h1" variant="h5" sx={{ flex: '1 1 260px', fontWeight: 700 }}>Servers &amp; Resources</Typography>
      <Tooltip title={received ? `Last received: ${timestamp(received)}` : 'No received samples'}>
        <Typography variant="body2" color="text.secondary">Last received {received ? age(received, now) : 'never'}</Typography>
      </Tooltip>
      <Tooltip title="Refresh stored observations"><span><IconButton aria-label="Refresh stored observations" disabled={busy || !visible} onClick={refresh}><RefreshRoundedIcon /></IconButton></span></Tooltip>
    </Stack>
    <Stack direction="row" gap={1.5} flexWrap="wrap" alignItems="center">
      <TextField select size="small" label="Environment" value={environment} sx={{ minWidth: 180 }} onChange={event => { setEnvironment(event.target.value); setRole(''); setSelection(null) }}>
        <MenuItem value="">All environments</MenuItem>{environments.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}
      </TextField>
      <ToggleButtonGroup exclusive size="small" value={range} aria-label="History range" onChange={(_, value: MonitoringRange | null) => { if (value) setRange(value) }}>
        {(['1h', '24h', '7d'] as const).map(value => <ToggleButton key={value} value={value} aria-label={`History range ${value}`}>{value}</ToggleButton>)}
      </ToggleButtonGroup>
      <Typography variant="caption" color="text.secondary">Collection ~60s / Stored data refresh 30s{!visible ? ' / Paused' : ''}</Typography>
    </Stack>
    {busy && <LinearProgress aria-label="Loading server observations" />}
    {fleet.isError && <Alert severity="error">Infrastructure observations could not be refreshed.{fleet.data ? ' Last returned observations remain visible with their timestamps.' : ''}</Alert>}
    {fleet.data?.sources.map(source => <Alert key={source.id} severity={source.status === 'AVAILABLE' ? 'success' : source.status === 'DISABLED' ? 'info' : 'warning'}
      sx={{ py: 0, overflowWrap: 'anywhere' }} icon={source.status === 'AVAILABLE' ? false : undefined}>
      <strong>{source.id}</strong>: {source.status === 'AVAILABLE' ? 'Available' : source.status === 'DISABLED' ? 'Disabled' : 'Unavailable'}{source.message ? ` / ${source.message}` : ''}
    </Alert>)}
    {!fleet.data && !fleet.isError && <Typography color="text.secondary" role="status">Loading server observations...</Typography>}
    {fleet.data && <>
      {servers.length ? <ServerOverview servers={servers} now={now} onSelect={serverId => setSelection({ serverId })} />
        : <Alert severity="info">{allServers.length ? 'No servers match this environment.' : 'No servers have been returned by the configured monitoring sources.'}</Alert>}
      {servers.some(server => !server.complete && server.observedAt) && <Alert severity="warning">Partial observations: resource counts and footprints may be incomplete.</Alert>}
      {servers.some(server => !server.host && server.observedAt) && <Alert severity="warning">Host measurements are unavailable for one or more returned samples.</Alert>}
      {servers.filter(server => server.warnings.length > 0).map(server => <Box component="section" key={server.serverId}>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Attention / {server.displayName} / {timestamp(server.observedAt)}</Typography>
        <Stack spacing={1}>{server.warnings.map((warning, index) => <Alert severity="warning" key={`${index}-${warning}`} sx={{ overflowWrap: 'anywhere' }}>{warning}</Alert>)}</Stack>
      </Box>)}
      {trendServer && <Box component="section">
        <Stack direction="row" gap={2} alignItems="center" flexWrap="wrap" sx={{ mb: 2 }}>
          <Typography component="h2" variant="h6">Host history</Typography>
          <TextField size="small" select label="History server" value={trendServer.serverId} sx={{ minWidth: 180, maxWidth: '100%' }} onChange={event => setTrendServerId(event.target.value)}>
            {servers.map(server => <MenuItem key={server.serverId} value={server.serverId}>{server.displayName}</MenuItem>)}
          </TextField>
        </Stack>
        <MonitoringHistory scope={scope} serverId={trendServer.serverId} range={range} visible={visible} now={now} />
      </Box>}
      <Divider />
      <Stack direction="row" alignItems="center" flexWrap="wrap" gap={1.5}>
        <Typography component="h2" variant="h6">Top consumers</Typography>
        <ToggleButtonGroup size="small" exclusive value={metric} aria-label="Rank top consumers" onChange={(_, value: ConsumerMetric | null) => { if (value) setMetric(value) }}>
          <ToggleButton value="memoryWorkingSetBytes" aria-label="Rank by memory"><MemoryRoundedIcon sx={{ mr: 0.5 }} />Memory</ToggleButton>
          <ToggleButton value="cpuCoresUsed" aria-label="Rank by CPU"><SpeedRoundedIcon sx={{ mr: 0.5 }} />CPU</ToggleButton>
          <ToggleButton value="restartCount" aria-label="Rank by restarts"><RestartAltRoundedIcon sx={{ mr: 0.5 }} />Restarts</ToggleButton>
        </ToggleButtonGroup>
        <TextField select size="small" label="Role" value={role} sx={{ minWidth: 160, maxWidth: '100%' }} onChange={event => setRole(event.target.value)}>
          <MenuItem value="">All roles</MenuItem>{roles.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}
        </TextField>
        <TextField size="small" label="Search resources" value={search} onChange={event => setSearch(event.target.value)} sx={{ flex: '1 1 220px' }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }} />
        <Chip label={`${rows.length} containers`} size="small" variant="outlined" />
      </Stack>
      {rows.length ? <ResourceConsumers rows={rows} now={now} onSelect={setSelection} /> : <Alert severity="info">No resource samples match these filters.</Alert>}
    </>}
    <Typography component="footer" variant="caption" color="text.secondary" sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
      Metrics are observations only. They do not govern deployment admission, workspace quotas, or lifecycle authorization.
    </Typography>
    {selection && <MonitoringInspector selection={selection} fleetServer={allServers.find(server => server.serverId === selection.serverId)} scope={scope}
      range={range} visible={visible} now={now} onClose={() => setSelection(null)} />}
  </Stack>
}
