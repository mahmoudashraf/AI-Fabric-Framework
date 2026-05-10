import AutoAwesomeRoundedIcon from '@mui/icons-material/AutoAwesomeRounded'
import AutoFixHighRoundedIcon from '@mui/icons-material/AutoFixHighRounded'
import ApprovalRoundedIcon from '@mui/icons-material/ApprovalRounded'
import BugReportRoundedIcon from '@mui/icons-material/BugReportRounded'
import ChecklistRoundedIcon from '@mui/icons-material/ChecklistRounded'
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded'
import DatasetLinkedRoundedIcon from '@mui/icons-material/DatasetLinkedRounded'
import FactCheckRoundedIcon from '@mui/icons-material/FactCheckRounded'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded'
import HttpsRoundedIcon from '@mui/icons-material/HttpsRounded'
import InsightsRoundedIcon from '@mui/icons-material/InsightsRounded'
import LayersRoundedIcon from '@mui/icons-material/LayersRounded'
import MemoryRoundedIcon from '@mui/icons-material/MemoryRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import NotificationsActiveRoundedIcon from '@mui/icons-material/NotificationsActiveRounded'
import PsychologyAltRoundedIcon from '@mui/icons-material/PsychologyAltRounded'
import PrecisionManufacturingRoundedIcon from '@mui/icons-material/PrecisionManufacturingRounded'
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded'
import SmartToyRoundedIcon from '@mui/icons-material/SmartToyRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import StoreRoundedIcon from '@mui/icons-material/StoreRounded'
import TuneRoundedIcon from '@mui/icons-material/TuneRounded'
import {
  AppBar,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material'
import { type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { type PlatformAuthSessionSummary } from '../api/platformApi'
import { DeploymentWorkspaceHeader } from '../components/DeploymentWorkspaceHeader'
import { DeploymentWorkspaceUnresolvedState } from '../components/DeploymentWorkspaceUnresolvedState'
import { isDeploymentWorkspacePath, useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'

const drawerWidth = 280

const navItems = [
  { label: 'Deployments', path: '/deployments', icon: <RocketLaunchRoundedIcon /> },
  { label: 'Overview', path: '/overview', icon: <DashboardRoundedIcon /> },
  { label: 'Actions', path: '/actions', icon: <AutoAwesomeRoundedIcon /> },
  { label: 'Approvals', path: '/approvals', icon: <ApprovalRoundedIcon /> },
  { label: 'Customers', path: '/customers', icon: <ApartmentRoundedIcon />, customerManagement: true },
  { label: 'Knowledge', path: '/knowledge', icon: <DatasetLinkedRoundedIcon /> },
  { label: 'Marketplace', path: '/marketplace', icon: <StorefrontRoundedIcon /> },
  { label: 'Inference Services', path: '/inference-services', icon: <MemoryRoundedIcon />, platformAdminOnly: true },
  { label: 'Product Services', path: '/product-services', icon: <PrecisionManufacturingRoundedIcon />, platformAdminOnly: true },
  { label: 'Partner Privileges', path: '/partner-privileges', icon: <ManageAccountsRoundedIcon />, platformAdminOnly: true },
  { label: 'Shopify Profiles', path: '/shopify-package-profiles', icon: <TuneRoundedIcon />, platformAdminOnly: true },
  { label: 'Shopify Launch', path: '/shopify-launch-readiness', icon: <RocketLaunchRoundedIcon />, platformAdminOnly: true },
  { label: 'Shopify Readiness', path: '/shopify-readiness-audit', icon: <FactCheckOutlinedIcon />, platformAdminOnly: true },
  { label: 'Shopify Stores', path: '/shopify-stores', icon: <StoreRoundedIcon />, platformAdminOnly: true },
  { label: 'Thinker Resolver', path: '/thinker-resolver', icon: <PsychologyAltRoundedIcon />, platformAdminOnly: true },
  { label: 'POC', path: '/poc', icon: <SmartToyRoundedIcon /> },
  { label: 'Prompts', path: '/prompts', icon: <PsychologyAltRoundedIcon /> },
  { label: 'Providers', path: '/providers', icon: <LayersRoundedIcon /> },
  { label: 'Security', path: '/security', icon: <HttpsRoundedIcon /> },
  { label: 'Verification', path: '/verification', icon: <FactCheckRoundedIcon /> },
  { label: 'Verification Ops', path: '/verification-ops', icon: <ChecklistRoundedIcon />, platformAdminOnly: true },
  { label: 'Vectorization', path: '/vectorization', icon: <AutoFixHighRoundedIcon /> },
  { label: 'Revisions', path: '/revisions', icon: <HistoryRoundedIcon /> },
  { label: 'Diagnostics', path: '/diagnostics', icon: <InsightsRoundedIcon /> },
  { label: 'Notifications', path: '/notifications', icon: <NotificationsActiveRoundedIcon />, platformAdminOnly: true },
  { label: 'Platform Diagnostics', path: '/platform-diagnostics', icon: <BugReportRoundedIcon />, platformAdminOnly: true },
  { label: 'User Access', path: '/users', icon: <ManageAccountsRoundedIcon />, userDirectory: true },
]

type AppShellProps = {
  children: ReactNode
  session: PlatformAuthSessionSummary | null
  onSignOut: () => Promise<void>
}

export function AppShell({ children, session, onSignOut }: AppShellProps) {
  const location = useLocation()
  const workspace = useDeploymentWorkspace()
  const showUnresolvedDeploymentState = workspace.isScopedPage
    && !workspace.deploymentsLoading
    && workspace.unresolvedRequestedDeploymentId != null
  const visibleNavItems = navItems.filter((item) => {
    if (item.platformAdminOnly) {
      return session?.enabled ? session.canManageUsers : true
    }
    if (item.customerManagement) {
      return session?.enabled ? session.canManageCustomers : true
    }
    if (item.userDirectory) {
      return session?.enabled ? session.canManageUserDirectory : true
    }
    return true
  })

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            borderRight: '1px solid',
            borderColor: 'divider',
            backgroundImage: 'linear-gradient(180deg, rgba(12,19,34,0.98), rgba(20,33,52,0.94))',
          },
        }}
      >
        <Toolbar sx={{ px: 3, pt: 2, pb: 1, alignItems: 'flex-start' }}>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1.25} alignItems="center">
              <Box
                sx={{
                  width: 42,
                  height: 42,
                  borderRadius: 2,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  boxShadow: '0 10px 30px rgba(75, 156, 211, 0.35)',
                }}
              >
                <AutoAwesomeRoundedIcon />
              </Box>
              <Box>
                <Typography variant="h6" sx={{ color: 'common.white', lineHeight: 1.1 }}>
                  AI Enablement
                </Typography>
                <Typography variant="body2" sx={{ color: 'grey.400' }}>
                  Control Plane
                </Typography>
              </Box>
            </Stack>
            <Chip
              label="Phase 21 Public API"
              color="primary"
              size="small"
              sx={{ alignSelf: 'flex-start', fontWeight: 700 }}
            />
          </Stack>
        </Toolbar>
        <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />
        <List sx={{ px: 1.5, py: 2 }}>
          {visibleNavItems.map((item) => {
            const active = location.pathname === item.path
            const target = isDeploymentWorkspacePath(item.path)
              ? workspace.buildWorkspacePath(item.path)
              : item.path
            return (
              <ListItemButton
                key={item.path}
                component={Link}
                to={target}
                selected={active}
                sx={{
                  borderRadius: 2,
                  mb: 0.5,
                  color: active ? 'common.white' : 'grey.300',
                  '&.Mui-selected': {
                    bgcolor: 'rgba(75, 156, 211, 0.18)',
                  },
                  '&.Mui-selected:hover': {
                    bgcolor: 'rgba(75, 156, 211, 0.24)',
                  },
                  '&:hover': {
                    bgcolor: 'rgba(255,255,255,0.04)',
                  },
                }}
              >
                <ListItemIcon sx={{ color: 'inherit', minWidth: 40 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            )
          })}
        </List>
      </Drawer>

      <Box sx={{ flexGrow: 1 }}>
        <AppBar
          position="sticky"
          elevation={0}
          sx={{
            backdropFilter: 'blur(12px)',
            bgcolor: 'rgba(245, 247, 250, 0.82)',
            borderBottom: '1px solid',
            borderColor: 'divider',
            color: 'text.primary',
          }}
        >
          <Toolbar sx={{ minHeight: 72 }}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', md: 'center' }}
              sx={{ width: '100%' }}
            >
              <Stack spacing={0.25}>
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Configurable AI Enablement Platform
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Runtime and connector remain immutable. Configuration is versioned and released from the platform.
                </Typography>
              </Stack>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                {session?.enabled ? (
                  <>
                    <Chip
                      label={session.role ?? 'AUTHENTICATED'}
                      color={session.role === 'PLATFORM_ADMIN' ? 'secondary' : 'primary'}
                      variant="outlined"
                    />
                    <Chip label={session.displayName ?? session.actorId ?? 'unknown'} variant="outlined" />
                    <Chip label={session.authenticationMode ?? 'AUTH'} variant="outlined" />
                    <Button
                      variant="outlined"
                      startIcon={<LogoutRoundedIcon />}
                      onClick={() => {
                        void onSignOut()
                      }}
                    >
                      Sign out
                    </Button>
                  </>
                ) : (
                  <Chip label="Auth disabled" color="warning" variant="outlined" />
                )}
              </Stack>
            </Stack>
          </Toolbar>
        </AppBar>
        <DeploymentWorkspaceHeader />
        <Box component="main" sx={{ p: 3.5 }}>
          {showUnresolvedDeploymentState ? <DeploymentWorkspaceUnresolvedState /> : children}
        </Box>
      </Box>
    </Box>
  )
}
