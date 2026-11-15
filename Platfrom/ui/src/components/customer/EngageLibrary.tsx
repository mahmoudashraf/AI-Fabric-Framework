import { Box, ButtonBase, Chip, InputAdornment, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import WidgetsOutlined from '@mui/icons-material/WidgetsOutlined'
import LanguageRounded from '@mui/icons-material/LanguageRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import type { AIWorkspaceInstallationSummary } from '../../api/platformApi'
import { engageState, type EngageFilter } from './engagePresentation'
import { safeCustomerAppUrl } from './customerResourceModels'

export function EngageLibrary({ apps, search, onSearch, filter, onFilter, selectedId, onInspect, loading, unavailable }: {
  apps: AIWorkspaceInstallationSummary[]; search: string; onSearch: (value: string) => void
  filter: EngageFilter; onFilter: (value: EngageFilter) => void; selectedId?: string
  onInspect: (id: string) => void
  loading?: boolean; unavailable?: boolean
}) {
  const visible = apps.filter(app => app.displayName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())
    && (filter === 'all' || (filter === 'active' ? app.status === 'ACTIVE' : ['DRAFT', 'ACTIVE'].includes(app.status) && engageState(app).label !== 'Active')))
  return <Box>
    <Stack direction="row" gap={2} flexWrap="wrap" justifyContent="space-between" sx={{ mb: 3 }}>
      <TextField size="small" placeholder="Search Engage apps" value={search} onChange={event => onSearch(event.target.value)}
        inputProps={{ 'aria-label': 'Search Engage apps' }} sx={{ flex: '1 1 220px', maxWidth: 470 }}
        InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> }} />
      <ToggleButtonGroup exclusive size="small" value={filter} aria-label="Filter Engage apps" onChange={(_, value: EngageFilter | null) => { if (value) onFilter(value) }}
        sx={{ '& .MuiToggleButton-root': { textTransform: 'none', px: 1.5 } }}>
        <ToggleButton value="all">All</ToggleButton><ToggleButton value="setup">Setup needed</ToggleButton><ToggleButton value="active">Active</ToggleButton>
      </ToggleButtonGroup>
    </Stack>
    <Stack component="ul" aria-label="Engage apps" spacing={1} sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {visible.map(app => {
        const state = engageState(app), embedded = app.deliveryMode === 'embedded', address = safeCustomerAppUrl(app.appUrl)
        return <Box component="li" key={app.installationId}><ButtonBase onClick={() => onInspect(app.installationId)} aria-label={`Inspect ${app.displayName}`} aria-pressed={selectedId === app.installationId}
          sx={{ width: '100%', minHeight: 88, display: 'grid', gridTemplateColumns: '44px minmax(0,1fr) 18px', gap: 2, p: 2, textAlign: 'left', border: 1,
            borderColor: selectedId === app.installationId ? 'primary.main' : 'divider', borderRadius: '7px', bgcolor: selectedId === app.installationId ? theme => alpha(theme.palette.primary.main, .055) : 'background.paper',
            '&:hover': { bgcolor: 'action.hover' }, '&.Mui-focusVisible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 } }}>
          <Box sx={{ width: 44, height: 48, display: 'grid', placeItems: 'center', borderRadius: '6px', bgcolor: theme => alpha(theme.palette.secondary.main, .1), color: 'secondary.main' }}><WidgetsOutlined /></Box>
          <Box sx={{ minWidth: 0 }}><Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap">
            <Typography fontWeight={650} sx={{ fontSize: 16, overflowWrap: 'anywhere' }}>{app.displayName}</Typography><Chip size="small" label={state.label} color={state.color} sx={{ height: 23 }} />
          </Stack><Stack direction="row" gap={.75} alignItems="center" sx={{ mt: .75, color: 'text.secondary' }}>{embedded ? <CodeRounded sx={{ fontSize: 17 }} /> : <LanguageRounded sx={{ fontSize: 17 }} />}
            <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>{embedded ? 'Website widget' : 'Standalone app'}{embedded ? app.allowedOrigins.length ? ` · ${app.allowedOrigins.length} website${app.allowedOrigins.length === 1 ? '' : 's'}` : ' · Website not set' : address ? ` · ${new URL(address).hostname}` : ' · Address not set'}</Typography>
          </Stack></Box><ArrowForwardRounded sx={{ fontSize: 18 }} />
        </ButtonBase></Box>
      })}
    </Stack>
    {!visible.length && !loading && <Typography role="status" color="text.secondary" sx={{ py: 3 }}>{unavailable ? 'The app list is unavailable.' : search.trim() ? 'No Engage apps match your search.' : filter === 'active' ? 'No active Engage apps.' : filter === 'setup' ? 'No apps need setup.' : 'No Engage apps yet.'}</Typography>}
  </Box>
}
