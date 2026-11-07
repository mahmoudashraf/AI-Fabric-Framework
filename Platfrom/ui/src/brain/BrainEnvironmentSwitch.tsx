import { useState } from 'react'
import { Box, Button, Dialog, DialogContent, DialogTitle, IconButton, List, ListItemButton,
  ListItemText, Stack, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import type { DeploymentReleaseSummary, DeploymentTargetProfileSummary, DeploymentVersionSummary } from '../api/platformApi'
import { environmentLabel, groupEnvironmentTargets, releaseInProgress, targetReleaseState } from './brainReleasePresentation'

export function BrainEnvironmentSwitch({ profiles, releases, versions, selectedId, unavailable, loading, disabled, onChange }: {
  profiles: DeploymentTargetProfileSummary[]
  releases: DeploymentReleaseSummary[]
  versions: DeploymentVersionSummary[]
  selectedId: string
  unavailable: boolean
  loading: boolean
  disabled: boolean
  onChange: (targetId: string) => void
}) {
  const [chooser, setChooser] = useState<string | null>(null)
  const groups = groupEnvironmentTargets(profiles)
  const selected = profiles.find(item => item.id === selectedId)
  const locked = disabled || loading || unavailable || releases.some(releaseInProgress)
  const choose = (environment: string) => {
    if (locked || loading || unavailable) return
    const targets = profiles.filter(item => item.environmentName === environment && item.active)
    if (targets.length === 1) onChange(targets[0].id)
    else if (targets.length > 1) setChooser(environment)
  }
  return <Stack direction="row" alignItems="center" gap={1.5} flexWrap="wrap" data-brain-environments>
    <Typography fontSize={12} color="text.secondary" fontWeight={600}>Environment</Typography>
    <ToggleButtonGroup exclusive value={selected?.environmentName ?? null} aria-label="Environment" disabled={locked || loading || unavailable}
      sx={{ gap: 0.75, flexWrap: 'wrap', '& .MuiToggleButtonGroup-grouped': { border: '1px solid', borderColor: 'divider', borderRadius: '6px !important', m: '0 !important' } }}>
      {groups.map(([environment, targets]) => {
        const target = targets.length === 1 ? targets[0] : selected?.environmentName === environment ? selected : null
        const state = target ? targetReleaseState(releases, target.id) : null
        const version = versions.find(item => item.id === state?.active?.deploymentVersionId)?.versionLabel
          ?? state?.active?.deploymentVersionId
        return <ToggleButton key={environment} value={environment} aria-label={`View ${environmentLabel(environment)}`}
          onClick={() => choose(environment)} disabled={locked || loading || unavailable || !targets.some(item => item.active)}
          sx={{ textTransform: 'none', textAlign: 'left', px: 1.5, py: 0.65, minWidth: 145, justifyContent: 'flex-start',
            '&.Mui-selected': { borderColor: 'primary.main', bgcolor: 'action.selected', color: 'text.primary' } }}>
          <Box><Typography fontSize={13} fontWeight={650}>{environmentLabel(environment)}</Typography>
            <Typography component="span" display="block" fontSize={11} color={!unavailable && state && state.color !== 'default' ? `${state.color}.main` : 'text.secondary'}>
              {unavailable ? 'Status unavailable' : loading ? 'Loading...' : target ? `${version ? `${version} · ` : ''}${state?.label}` : `${targets.filter(item => item.active).length} destinations`}
            </Typography></Box>
        </ToggleButton>
      })}
    </ToggleButtonGroup>
    {!groups.length && <Typography variant="caption" color="text.secondary">{loading ? 'Loading environments...' : unavailable ? 'Environments unavailable' : 'No authorized environment'}</Typography>}
    {selected && (groups.find(([name]) => name === selected.environmentName)?.[1].length ?? 0) > 1 &&
      <Tooltip title={selected.name}><Button size="small" disabled={locked} onClick={() => setChooser(selected.environmentName)}>Destination details</Button></Tooltip>}
    <Dialog open={chooser !== null} onClose={() => setChooser(null)} fullWidth maxWidth="sm" aria-labelledby="brain-target-title">
      <DialogTitle id="brain-target-title" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>Choose {environmentLabel(chooser ?? '')} destination
        <IconButton aria-label="Close environment choices" onClick={() => setChooser(null)}><CloseRounded /></IconButton>
      </DialogTitle>
      <DialogContent dividers><List disablePadding>{profiles.filter(item => item.environmentName === chooser).map(item => <ListItemButton key={item.id}
        selected={item.id === selectedId} disabled={!item.active || locked} onClick={() => { if (!locked && item.active) { onChange(item.id); setChooser(null) } }}>
        <ListItemText primary={item.name} secondary={item.active ? item.id : 'Inactive destination'} />
      </ListItemButton>)}</List></DialogContent>
    </Dialog>
  </Stack>
}
