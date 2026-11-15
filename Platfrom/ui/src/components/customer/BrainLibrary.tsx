import { Alert, Box, ButtonBase, Chip, CircularProgress, InputAdornment, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import PsychologyOutlined from '@mui/icons-material/PsychologyOutlined'
import MenuBookOutlined from '@mui/icons-material/MenuBookOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import LinkRounded from '@mui/icons-material/LinkRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import { filterBrainLibrary, type BrainLibraryFilter, type BrainLibraryState, type CustomerResource } from './customerResourceModels'

export type BrainLibraryRow = { resource: CustomerResource; state: BrainLibraryState }

export function BrainLibrary({ rows, search, onSearch, filter, onFilter, selectedId, onInspect }: {
  rows: BrainLibraryRow[]
  search: string
  onSearch: (value: string) => void
  filter: BrainLibraryFilter
  onFilter: (value: BrainLibraryFilter) => void
  selectedId?: string
  onInspect: (resource: CustomerResource) => void
}) {
  const filtered = filterBrainLibrary(rows, search, filter)
  const liveLoading = filter === 'live' && rows.some(row => row.state.liveEvidence === 'loading')
  return <Box sx={{ containerType: 'inline-size', letterSpacing: 0, '& .MuiTypography-root': { letterSpacing: 0 } }}>
    <Stack direction="row" gap={2} flexWrap="wrap" justifyContent="space-between" sx={{ mb: 3 }}>
      <TextField size="small" placeholder="Search Smart Brains" value={search} onChange={event => onSearch(event.target.value)}
        inputProps={{ 'aria-label': 'Search Smart Brains' }} sx={{ flex: '1 1 230px', maxWidth: 470 }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> }} />
      <ToggleButtonGroup exclusive size="small" value={filter} aria-label="Filter Smart Brains"
        onChange={(_, value: BrainLibraryFilter | null) => { if (value) onFilter(value) }}
        sx={{ gap: 1, '& .MuiToggleButtonGroup-grouped': { border: 1, borderColor: 'divider', borderRadius: '6px !important', px: 2, py: 0.75, minWidth: 66, textTransform: 'none', letterSpacing: 0 },
          '& .MuiToggleButtonGroup-grouped.Mui-selected': { color: 'primary.main', bgcolor: theme => alpha(theme.palette.primary.main, 0.12) } }}>
        <ToggleButton value="all">All</ToggleButton><ToggleButton value="drafts">Drafts</ToggleButton><ToggleButton value="live">Live</ToggleButton>
      </ToggleButtonGroup>
    </Stack>
    {liveLoading ? <Stack role="status" direction="row" gap={1} alignItems="center" sx={{ mb: 2 }}><CircularProgress size={16} /><Typography variant="body2" color="text.secondary">Loading live status...</Typography></Stack> : null}
    {filter === 'live' && rows.some(row => row.state.liveEvidence === 'error')
      ? <Alert severity="warning" sx={{ mb: 2 }}>Live status could not be loaded for some Brains.</Alert> : null}
    {filter === 'live' && rows.some(row => row.state.liveUnknown && !['loading', 'error'].includes(row.state.liveEvidence))
      ? <Typography role="status" variant="body2" color="text.secondary" sx={{ mb: 2 }}>Live status is unavailable for some Brains.</Typography> : null}
    <Stack component="ul" aria-label="Smart Brains" spacing={1} sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {filtered.map(({ resource, state }) => <Box component="li" key={resource.id}>
        <ButtonBase onClick={() => onInspect(resource)} aria-label={`Inspect ${resource.name}`} aria-pressed={selectedId === resource.id}
          sx={{ width: '100%', textAlign: 'left', minHeight: 88, display: 'grid', gridTemplateColumns: '44px minmax(0,1fr) 18px', gap: 2,
            border: 1, borderColor: selectedId === resource.id ? 'primary.main' : 'divider', borderRadius: '7px', p: 2,
            bgcolor: selectedId === resource.id ? theme => alpha(theme.palette.primary.main, 0.055) : 'background.paper',
            boxShadow: selectedId === resource.id ? theme => `inset 4px 0 ${theme.palette.primary.main}` : 'none',
            '&:hover': { bgcolor: 'action.hover' }, '&.Mui-focusVisible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 } }}>
          <Box sx={{ width: 44, height: 48, borderRadius: '6px', color: 'primary.main', bgcolor: theme => alpha(theme.palette.primary.main, 0.09), display: 'grid', placeItems: 'center' }}><PsychologyOutlined sx={{ fontSize: 28 }} /></Box>
          <Box sx={{ minWidth: 0, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
            <Box sx={{ minWidth: 0, flex: '1 1 180px' }}>
              <Typography fontWeight={650} sx={{ fontSize: 16, overflowWrap: 'anywhere', lineHeight: 1.4 }}>{resource.name}</Typography>
              <Tooltip title={`${state.label}. ${state.liveLabel}`}><Chip label={state.label} color={state.color} size="small"
                icon={<Box component="span" sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: 'currentColor' }} />}
                sx={{ mt: 0.6, height: 23, bgcolor: theme => alpha(state.color === 'default' ? theme.palette.text.primary : theme.palette[state.color].main, 0.12),
                  color: state.color === 'default' ? 'text.secondary' : `${state.color}.main`, '& .MuiChip-icon': { color: 'inherit', ml: 1 } }} /></Tooltip>
            </Box>
            <Stack direction="row" gap={2} alignItems="center" flexWrap="wrap" sx={{ color: 'text.secondary', '& > span': { display: 'inline-flex', alignItems: 'center', gap: 0.75, fontSize: 12, lineHeight: 1.5 },
              '@container (max-width: 620px)': { gap: 1.5 }, '@container (max-width: 380px)': { '& > span': { fontSize: 11 } } }}>
              <Box component="span"><MenuBookOutlined sx={{ fontSize: 18 }} />Knowledge</Box>
              <Box component="span"><LayersOutlined sx={{ fontSize: 18 }} />Capabilities</Box>
              <Box component="span"><LinkRounded sx={{ fontSize: 18 }} />{state.connectionLabel}</Box>
            </Stack>
          </Box>
          <ArrowForwardRounded sx={{ fontSize: 18, color: 'text.primary' }} />
        </ButtonBase>
      </Box>)}
    </Stack>
    {!filtered.length ? <Typography role="status" color="text.secondary" sx={{ py: 3 }}>{search.trim() ? 'No Smart Brains match your search.'
      : liveLoading ? 'Checking production releases...' : filter === 'live' ? 'No confirmed live Brains in the available release details.' : filter === 'drafts' ? 'No draft Brains.' : 'No Smart Brains yet.'}</Typography> : null}
  </Box>
}
