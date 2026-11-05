import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import { Alert, Box, IconButton, LinearProgress, Stack, Table, TableBody, TableCell, TableHead, TableRow, Tooltip, Typography } from '@mui/material'
import { useEffect, useId, useRef, useState } from 'react'
import type { MonitoringHistory as History, MonitoringRange } from '../../api/infrastructureMonitoringApi'
import { bytes, cores, finite, historySeries, number, percent, rate, timestamp } from './monitoringModels'
import { useMonitoringHistory } from './useMonitoringQueries'

type Metric = { key: string; label: string; format: (value: unknown) => string; color: 'primary' | 'secondary' | 'success' | 'warning' }
const hostMetrics: Metric[] = [
  { key: 'cpuUsagePercent', label: 'Server CPU (%)', format: percent, color: 'primary' },
  { key: 'memoryAvailableBytes', label: 'Available memory', format: bytes, color: 'success' },
  { key: 'swapInBytesPerSecond', label: 'Swap in (bytes/s)', format: rate, color: 'warning' },
  { key: 'diskFreeBytes', label: 'Free disk space', format: bytes, color: 'secondary' },
]
const resourceMetrics: Metric[] = [
  { key: 'memoryWorkingSetBytes', label: 'Working-set memory', format: bytes, color: 'success' },
  { key: 'cpuCoresUsed', label: 'Container CPU (core-equivalents)', format: cores, color: 'primary' },
  { key: 'cpuThrottledPeriodsPercent', label: 'Quota-throttled periods (%)', format: percent, color: 'warning' },
  { key: 'restartCount', label: 'Restart count', format: value => number(value, 0), color: 'secondary' },
]

function HistoryChart({ history, metric, now }: { history: History; metric: Metric; now: number }) {
  const id = useId()
  const plot = useRef<SVGSVGElement | null>(null)
  const [width, setWidth] = useState(600)
  const series = historySeries(history.points, metric.key, history.bucketSeconds, history.range, now, width)
  useEffect(() => {
    if (!plot.current) return
    const observer = new ResizeObserver(entries => setWidth(Math.max(280, entries[0].contentRect.width)))
    observer.observe(plot.current)
    return () => observer.disconnect()
  }, [series.count > 0])
  return <Box component="section" sx={{ minWidth: 0 }} aria-labelledby={`${id}-heading`}>
    <Typography id={`${id}-heading`} variant="subtitle2">{metric.label}</Typography>
    {series.count === 0 ? <Box sx={{ minHeight: 150, display: 'flex', alignItems: 'center', color: 'text.secondary' }}>
      No measurements in this window.
    </Box> : <Box component="svg" ref={plot} viewBox={`0 0 ${width} 150`} role="img" aria-labelledby={`${id}-title ${id}-description`}
      sx={{ width: '100%', height: 150, display: 'block', overflow: 'visible', color: `${metric.color}.main` }}>
      <title id={`${id}-title`}>{metric.label} history</title>
      <desc id={`${id}-description`}>Mean {metric.format(series.mean)}. Observed maximum {metric.format(series.peak)}.
        {' '}{series.count} measured buckets. Solid line: bucket mean. Dashed line: bucket maximum. Missing samples are gaps.</desc>
      {[0, 0.5, 1].map(fraction => <g key={fraction}>
        <Box component="line" x1="64" x2={width - 24} y1={117 - fraction * 99} y2={117 - fraction * 99}
          sx={theme => ({ stroke: theme.palette.divider, strokeWidth: 1 })} />
        <Box component="text" x="60" y={121 - fraction * 99} textAnchor="end" sx={theme => ({ fill: theme.palette.text.secondary, fontSize: 11 })}>
          {metric.format(series.ceiling * fraction)}
        </Box>
      </g>)}
      <path data-series="mean" d={series.meanPath} fill="none" stroke="currentColor" strokeWidth="2" />
      <path data-series="maximum" d={series.maxPath} fill="none" stroke="currentColor" strokeWidth="1" strokeDasharray="4 4" opacity="0.65" />
      {series.samples.map(({ point, time }, index) => finite(point.values[metric.key]) && <circle key={`${time}-${index}`}
        cx={series.x(time)} cy={series.y(point.values[metric.key]!)} r="2" fill="currentColor">
        <title>{timestamp(point.observedAt)}: {metric.format(point.values[metric.key])}; maximum {metric.format(point.maxValues[metric.key])}</title>
      </circle>)}
      <Box component="text" x="64" y="142" sx={theme => ({ fill: theme.palette.text.secondary, fontSize: 11 })}>
        {new Date(series.start).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
      </Box>
      <Box component="text" x={width - 24} y="142" textAnchor="end" sx={theme => ({ fill: theme.palette.text.secondary, fontSize: 11 })}>
        {new Date(series.end).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
      </Box>
    </Box>}
    <Typography variant="caption" component="p" color="text.secondary">
      Mean {metric.format(series.mean)} / Observed max {metric.format(series.peak)} / Coverage {series.count}{series.expected ? ` of ~${series.expected}` : ''} buckets
    </Typography>
    {series.samples.length > 0 && <Box component="details" sx={{ mt: 0.5, fontSize: 12, color: 'text.secondary' }}>
      <Box component="summary" sx={{ cursor: 'pointer', width: 'fit-content' }}>Samples: {metric.label}</Box>
      <Box sx={{ maxHeight: 240, overflow: 'auto' }}>
        <Table size="small" aria-label={`${metric.label} samples`}>
          <TableHead><TableRow><TableCell>Observed</TableCell><TableCell>Mean</TableCell><TableCell>Maximum</TableCell></TableRow></TableHead>
          <TableBody>{series.samples.map(({ point, time }, index) => <TableRow key={`${time}-${index}`}>
            <TableCell>{timestamp(point.observedAt)}</TableCell><TableCell>{metric.format(point.values[metric.key])}</TableCell>
            <TableCell>{metric.format(point.maxValues[metric.key])}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </Box>
    </Box>}
  </Box>
}

export function MonitoringHistory({ scope, serverId, resourceId, range, visible, now }: {
  scope: string; serverId?: string; resourceId?: string; range: MonitoringRange; visible: boolean; now: number
}) {
  const query = useMonitoringHistory(scope, serverId, range, visible, resourceId)
  if (!serverId) return null
  return <Stack spacing={1.5}>
    {query.isFetching && <LinearProgress aria-label="Loading resource history" />}
    {query.isError && <Alert severity="warning" action={<Tooltip title="Retry stored history"><IconButton color="inherit"
      aria-label="Retry stored history" disabled={!visible || query.isFetching} onClick={() => void query.refetch()}><RefreshRoundedIcon /></IconButton></Tooltip>}>
      History unavailable.{query.data ? ' Last returned history is shown.' : ''}
    </Alert>}
    {!query.data && !query.isError && <Typography color="text.secondary" role="status">{visible ? 'Loading history...' : 'History paused while hidden.'}</Typography>}
    {query.data && <>
      <Typography variant="caption" color="text.secondary">{number(query.data.bucketSeconds, 0)}s buckets / {range} / Bucket mean and maximum</Typography>
      {query.data.points.length === 0 ? <Alert severity="info">No history received for this window.</Alert>
        : <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: resourceId ? 'minmax(0, 1fr)' : 'repeat(2, minmax(0, 1fr))' }, gap: 2.5 }}>
          {(resourceId ? resourceMetrics : hostMetrics).map(metric => <HistoryChart key={metric.key} history={query.data} metric={metric} now={now} />)}
        </Box>}
    </>}
  </Stack>
}
