import {
  AppBar,
  Avatar,
  Badge,
  Box,
  Button,
  Divider,
  IconButton,
  InputBase,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import AddTaskOutlinedIcon from '@mui/icons-material/AddTaskOutlined'
import AutoAwesomeOutlinedIcon from '@mui/icons-material/AutoAwesomeOutlined'
import BellOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined'
import ChevronRightOutlinedIcon from '@mui/icons-material/ChevronRightOutlined'
import CollectionsBookmarkOutlinedIcon from '@mui/icons-material/CollectionsBookmarkOutlined'
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined'
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import PsychologyAltOutlinedIcon from '@mui/icons-material/PsychologyAltOutlined'
import SearchOutlinedIcon from '@mui/icons-material/SearchOutlined'
import SpaceDashboardOutlinedIcon from '@mui/icons-material/SpaceDashboardOutlined'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import SupportAgentOutlinedIcon from '@mui/icons-material/SupportAgentOutlined'
import { useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import type { PartnerSession } from '../api/schemas'
import { useSupabaseAuth } from '../auth/SupabaseProvider'

const railWidth = 244

const navItems = [
  { label: 'Dashboard', to: '/', icon: SpaceDashboardOutlinedIcon },
  { label: 'Client stores', to: '/stores', icon: StorefrontOutlinedIcon },
  { label: 'New implementation', to: '/implementations/new', icon: AddTaskOutlinedIcon, cta: true },
  { label: 'Intelligence catalog', to: '/catalog', icon: AutoAwesomeOutlinedIcon },
  { label: 'Templates & playbooks', to: '/templates', icon: CollectionsBookmarkOutlinedIcon },
  { label: 'Thinker sessions', to: '/thinker', icon: PsychologyAltOutlinedIcon },
  { label: 'Verification packs', to: '/verification', icon: FactCheckOutlinedIcon },
  { label: 'Evidence bundles', to: '/evidence', icon: FolderZipOutlinedIcon },
  { label: 'Support', to: '/support', icon: SupportAgentOutlinedIcon, badge: true },
  { label: 'Documentation', to: '/docs', icon: MenuBookOutlinedIcon },
]

export function AppShell({
  children,
  session,
  mode,
  onToggleMode,
}: {
  children: ReactNode
  session: PartnerSession | null
  mode: 'light' | 'dark'
  onToggleMode: () => void
}) {
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('md'))
  const navigate = useNavigate()
  const { signOut } = useSupabaseAuth()
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const workspaceName = session?.account?.name ?? 'Partner workspace'
  const avatarLetter = session?.member?.displayName?.[0] ?? session?.member?.email?.[0] ?? 'P'

  const handleSignOut = async () => {
    setMenuAnchor(null)
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        component="nav"
        sx={{
          position: 'fixed',
          inset: compact ? 'auto 0 0 0' : '0 auto 0 0',
          width: compact ? '100%' : railWidth,
          height: compact ? 64 : '100vh',
          bgcolor: 'background.paper',
          borderRight: compact ? 0 : 1,
          borderTop: compact ? 1 : 0,
          borderColor: 'divider',
          zIndex: theme.zIndex.drawer,
        }}
      >
        {!compact ? (
          <Stack spacing={0.5} sx={{ p: 2 }}>
            <Stack direction="row" spacing={1.25} alignItems="center" sx={{ px: 1, py: 1.5 }}>
              <Box sx={{ width: 28, height: 28, borderRadius: 2, bgcolor: 'primary.main', color: 'white', display: 'grid', placeItems: 'center', fontWeight: 800 }}>
                L
              </Box>
              <Typography variant="h3">LoomAI Partners</Typography>
            </Stack>
            <Divider sx={{ my: 1 }} />
            {navItems.map((item) => (
              <RailLink key={item.to} {...item} compact={false} count={item.badge ? session?.openEscalationCount : undefined} />
            ))}
          </Stack>
        ) : (
          <Stack direction="row" sx={{ height: 64, overflowX: 'auto', px: 1 }} alignItems="center">
            {navItems.slice(0, 8).map((item) => (
              <RailLink key={item.to} {...item} compact count={item.badge ? session?.openEscalationCount : undefined} />
            ))}
          </Stack>
        )}
      </Box>

      <AppBar
        position="fixed"
        color="transparent"
        elevation={0}
        sx={{
          ml: compact ? 0 : `${railWidth}px`,
          width: compact ? '100%' : `calc(100% - ${railWidth}px)`,
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
          backdropFilter: 'blur(8px)',
        }}
      >
        <Toolbar sx={{ gap: 2, minHeight: 64 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
            <Typography fontWeight={700} noWrap>{workspaceName}</Typography>
            <ChevronRightOutlinedIcon fontSize="small" color="disabled" />
            <Typography variant="caption" color="text.secondary" noWrap>
              Scoped implementation access
            </Typography>
          </Stack>
          <Box
            sx={{
              ml: 'auto',
              width: { xs: 160, md: 320 },
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              px: 1.25,
              py: 0.5,
              border: 1,
              borderColor: 'divider',
              borderRadius: 2,
              bgcolor: 'background.default',
            }}
          >
            <SearchOutlinedIcon fontSize="small" color="disabled" />
            <InputBase placeholder="Search workspace" sx={{ fontSize: '0.875rem', flex: 1 }} inputProps={{ 'aria-label': 'Search workspace' }} />
          </Box>
          <Button size="small" onClick={() => navigate('/docs')}>Help</Button>
          <Tooltip title="Open escalations">
            <IconButton onClick={() => navigate('/support')} aria-label="Open escalations">
              <Badge badgeContent={session?.openEscalationCount ?? 0} color="error">
                <BellOutlinedIcon />
              </Badge>
            </IconButton>
          </Tooltip>
          <Tooltip title={mode === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}>
            <IconButton onClick={onToggleMode} aria-label="Toggle color mode">
              {mode === 'light' ? <DarkModeOutlinedIcon /> : <LightModeOutlinedIcon />}
            </IconButton>
          </Tooltip>
          <IconButton onClick={(event) => setMenuAnchor(event.currentTarget)} aria-label="Account menu">
            <Avatar sx={{ width: 30, height: 30 }}>{avatarLetter.toUpperCase()}</Avatar>
          </IconButton>
          <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
            <MenuItem onClick={() => navigate('/settings/profile')}>Profile</MenuItem>
            <MenuItem onClick={() => navigate('/settings/members')}>Members</MenuItem>
            <Divider />
            <MenuItem onClick={handleSignOut}>
              <LogoutOutlinedIcon fontSize="small" sx={{ mr: 1 }} />
              Sign out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={{
          ml: compact ? 0 : `${railWidth}px`,
          pt: 10,
          pb: compact ? 10 : 4,
          px: { xs: 2, md: 3 },
          minHeight: '100vh',
        }}
      >
        {children}
      </Box>
    </Box>
  )
}

function RailLink({
  label,
  to,
  icon: Icon,
  cta,
  badge,
  count,
  compact,
}: {
  label: string
  to: string
  icon: typeof SpaceDashboardOutlinedIcon
  cta?: boolean
  badge?: boolean
  count?: number
  compact: boolean
}) {
  return (
    <Box
      component={NavLink}
      to={to}
      end={to === '/'}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.25,
        minWidth: compact ? 56 : 0,
        justifyContent: compact ? 'center' : 'flex-start',
        px: compact ? 1 : 1.25,
        py: 1,
        borderRadius: 2,
        color: 'text.secondary',
        textDecoration: 'none',
        fontWeight: 650,
        fontSize: '0.875rem',
        mt: cta && !compact ? 1.5 : 0,
        border: cta ? 1 : '1px solid transparent',
        borderColor: cta ? 'divider' : 'transparent',
        '&.active': {
          bgcolor: 'action.selected',
          color: 'text.primary',
        },
      }}
    >
      {badge ? (
        <Badge badgeContent={count ?? 0} color="error">
          <Icon fontSize="small" />
        </Badge>
      ) : (
        <Icon fontSize="small" />
      )}
      {!compact ? <span>{label}</span> : null}
    </Box>
  )
}
