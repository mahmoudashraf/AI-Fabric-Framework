import { Box, Button, Chip, Link, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { bytes, cores, deploymentPath, limit, number, type InspectorSelection, type ResourceRow } from './monitoringModels'
import { Freshness } from './ServerOverview'

export function ResourceConsumers({ rows, now, onSelect }: { rows: ResourceRow[]; now: number; onSelect: (selection: InspectorSelection) => void }) {
  return <TableContainer component={Box} sx={{ overflowX: 'auto', maxHeight: 560 }} tabIndex={0} aria-label="Top consumers scroll area">
    <Table size="small" stickyHeader sx={{ minWidth: 920 }} aria-label="Top resource consumers">
      <TableHead><TableRow>{['Resource / deployment', 'Server', 'Role', 'Working set / hard limit', 'Container CPU (cores)', 'Restarts', 'State / health'].map(label => <TableCell key={label}>{label}</TableCell>)}</TableRow></TableHead>
      <TableBody>{rows.map(({ server, resource }) => {
        const path = deploymentPath(resource)
        return <TableRow key={`${server.serverId}/${resource.resourceId}`} hover sx={{ '& td': { py: 1.25, verticalAlign: 'top' } }}>
          <TableCell sx={{ maxWidth: 280, overflowWrap: 'anywhere' }}><Button sx={{ p: 0, minWidth: 0, textAlign: 'left', justifyContent: 'flex-start' }}
            aria-label={`Inspect resource ${resource.name}`} onClick={() => onSelect({ serverId: server.serverId, resourceId: resource.resourceId })}>{resource.name}</Button>
            <Typography variant="caption" component="p" color="text.secondary">{path ? <Link component={RouterLink} to={path}>
              {resource.deploymentName || resource.deploymentId}</Link> : resource.providerKind === 'SHARED' ? 'Shared infrastructure' : 'Unmapped'}</Typography>
            {resource.targetProfileId && <Typography variant="caption" color="text.secondary">{resource.targetProfileId}</Typography>}
          </TableCell>
          <TableCell>{server.displayName}<Box sx={{ mt: 0.5 }}><Freshness server={server} now={now} /></Box></TableCell>
          <TableCell>{resource.role || 'Unknown'}</TableCell>
          <TableCell>{bytes(resource.memoryWorkingSetBytes)} / {limit(resource.memoryLimitBytes)}</TableCell>
          <TableCell>{cores(resource.cpuCoresUsed)}</TableCell>
          <TableCell>{number(resource.restartCount, 0)}{resource.oomKilled && <Chip size="small" color="error" label="OOM evidence" sx={{ ml: 0.5 }} />}</TableCell>
          <TableCell>{resource.state || 'Unknown'}<Typography variant="caption" component="p"
            color={resource.health?.toLowerCase() === 'unhealthy' ? 'error.main' : 'text.secondary'}>{resource.health ?? 'Health unknown'}</Typography></TableCell>
        </TableRow>
      })}</TableBody>
    </Table>
  </TableContainer>
}
