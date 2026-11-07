import { useState } from 'react'
import { Box, Button, Chip, IconButton, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary, DeploymentVersionSummary } from '../api/platformApi'
import { environmentLabel, groupEnvironmentTargets, targetReleaseState } from './brainReleasePresentation'

export function BrainReleaseVersions({ versions, profiles, releases, evidenceReady, selectedTargetId, selectedVersionId,
  all = false, busy, canOperate, onReview, onDetails, onActivity }: {
  versions: DeploymentVersionSummary[]
  profiles: DeploymentTargetProfileSummary[]
  releases: DeploymentReleaseSummary[]
  evidenceReady: boolean
  selectedTargetId: string
  selectedVersionId?: string
  all?: boolean
  busy: boolean
  canOperate: boolean
  onReview: (version: DeploymentVersionSummary) => void
  onDetails: (version: DeploymentVersionSummary) => void
  onActivity: () => void
}) {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const groups = groupEnvironmentTargets(profiles)
  const filtered = [...versions].sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))
    .filter(item => !all || `${item.versionLabel} ${item.id} ${item.status}`.toLowerCase().includes(search.toLowerCase()))
  const size = all ? 6 : 3
  const safePage = Math.min(page, Math.max(0, Math.ceil(filtered.length / size) - 1))
  const shown = filtered.slice(all ? safePage * size : 0, all ? (safePage + 1) * size : size)
  const selectedState = targetReleaseState(releases, selectedTargetId)
  return <Stack spacing={1.25} sx={{ minWidth: 0, flexShrink: 0 }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}>
      <Box><Typography component="h2" fontSize={19} fontWeight={650}>Published versions</Typography>
        <Typography variant="caption" color="text.secondary">{versions.length} {versions.length === 1 ? 'version' : 'versions'}</Typography></Box>
      {all && <TextField size="small" label="Find a version" value={search} onChange={event => { setSearch(event.target.value); setPage(0) }} />}
    </Stack>
    <TableContainer sx={{ minWidth: 0, overflowX: 'auto' }}><Table size="small" aria-label="Published versions and environment state" sx={{ minWidth: 430, '& th, & td': { px: 1, py: 0.5, overflowWrap: 'normal' }, '& .MuiChip-root': { fontSize: 11 } }}>
      <TableHead><TableRow><TableCell sx={{ minWidth: 75 }}>Version</TableCell>{groups.map(([name]) => <TableCell key={name} sx={{ whiteSpace: 'nowrap' }}>{environmentLabel(name)}</TableCell>)}<TableCell align="right">Action</TableCell></TableRow></TableHead>
      <TableBody>{shown.map(version => {
        const current = selectedState.active?.deploymentVersionId === version.id
        return <TableRow key={version.id} selected={version.id === selectedVersionId} hover>
          <TableCell><Button size="small" sx={{ p: 0, minWidth: 0, minHeight: 20, color: 'text.primary', fontWeight: 650 }} onClick={() => onDetails(version)}>{version.versionLabel}</Button>
            <Typography variant="caption" display="block" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>{version.status === 'PUBLISHED' ? 'Published' : version.status || 'Status unavailable'} · {version.publishedAt ? new Date(version.publishedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) : 'Date unavailable'}</Typography></TableCell>
          {groups.map(([name, targets]) => {
            const states = targets.map(target => {
            const state = targetReleaseState(releases, target.id)
            const active = state.active?.deploymentVersionId === version.id
            const pending = state.pending?.deploymentVersionId === version.id
            const failed = state.failed?.deploymentVersionId === version.id
            const label = !evidenceReady ? 'Unavailable' : pending ? state.label : active ? state.active?.status === 'VERIFYING' ? 'Verifying'
              : state.active?.verificationStatus === 'PASSED' && state.active.status === 'APPLIED_VERIFIED' ? 'Verified'
                : state.active?.status === 'APPLIED_VERIFICATION_FAILED' || state.active?.verificationStatus === 'FAILED' ? 'Verification failed' : 'Applied' : failed ? 'Last apply failed' : '—'
              return { target, label }
            })
            const visible = states.filter(item => item.label !== '—')
            const labels = [...new Set(visible.map(item => item.label))]
            return <TableCell key={name}><Tooltip title={states.map(item => `${item.target.name}: ${item.label}`).join('; ')}>
              <Stack spacing={0.5}>{labels.length ? labels.map(label => {
                const count = visible.filter(item => item.label === label).length
                return label === 'Unavailable' ? <Typography key={label} variant="caption" color="text.secondary">Unavailable</Typography>
                  : <Chip key={label} size="small" variant="outlined" label={`${label}${targets.length > 1 ? ` (${count})` : ''}`} color={label === 'Verified' ? 'success' : label === 'Last apply failed' ? 'error' : label === 'Verification failed' ? 'warning' : 'default'} />
              }) : <Typography variant="caption" color="text.secondary">—</Typography>}</Stack>
            </Tooltip></TableCell>
          })}
          <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
            {current ? <Button size="small" disabled={busy} onClick={onActivity}>View release</Button>
              : canOperate ? <Button size="small" variant="outlined" disabled={busy || !selectedTargetId || !evidenceReady} onClick={() => onReview(version)}>Review & apply</Button> : null}
            <Tooltip title={`View ${version.versionLabel} details`}><IconButton size="small" aria-label={`View ${version.versionLabel} details`} onClick={() => onDetails(version)}><ChevronRightRounded fontSize="small" /></IconButton></Tooltip>
          </TableCell>
        </TableRow>
      })}</TableBody>
    </Table></TableContainer>
    {filtered.length === 0 && <Typography variant="body2" color="text.secondary">{versions.length ? 'No versions match your search.' : 'No version published yet.'}</Typography>}
    {all && <TablePagination component="div" count={filtered.length} page={safePage} rowsPerPage={size} rowsPerPageOptions={[size]} onPageChange={(_, value) => setPage(value)} />}
  </Stack>
}
