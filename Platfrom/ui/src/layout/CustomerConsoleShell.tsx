import { useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  Alert, Avatar, Box, Button, Divider, Drawer, FormControl, IconButton,
  List, ListItemButton, ListItemIcon, ListItemText, MenuItem, Select,
  Stack, Toolbar, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import HubOutlined from '@mui/icons-material/HubOutlined'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import WidgetsOutlined from '@mui/icons-material/WidgetsOutlined'
import DatasetOutlined from '@mui/icons-material/DatasetOutlined'
import CableOutlined from '@mui/icons-material/CableOutlined'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import HistoryOutlined from '@mui/icons-material/HistoryOutlined'
import PeopleOutline from '@mui/icons-material/PeopleOutline'
import SupportAgentOutlined from '@mui/icons-material/SupportAgentOutlined'
import MenuRounded from '@mui/icons-material/MenuRounded'
import LogoutRounded from '@mui/icons-material/LogoutRounded'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import SpaceDashboardOutlined from '@mui/icons-material/SpaceDashboardOutlined'
import { type PlatformAuthSessionSummary } from '../api/platformApi'
import { fetchAIWorkspaceInstallations, fetchCustomerDeploymentPortfolio } from '../api/platformApi'
import { useQuery } from '@tanstack/react-query'
import { CustomerConsoleTheme, useCustomerColorPreference, type CustomerColorPreference } from '../theme/CustomerConsoleTheme'
import { isDeploymentWorkspacePath, useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { DeploymentWorkspaceUnresolvedState } from '../components/DeploymentWorkspaceUnresolvedState'

const navigation = [
  { path: '/home', label: 'Home', icon: SpaceDashboardOutlined },
  { path: '/deployments', label: 'Deployments', icon: DnsOutlined },
  { path: '/ai-workspaces', label: 'Engage apps', icon: WidgetsOutlined },
  { path: '/document-knowledge', label: 'Knowledge & data', icon: DatasetOutlined },
  { path: '/integrations', label: 'Connections', icon: CableOutlined },
  { path: '/human-support', label: 'Human support', icon: SupportAgentOutlined },
  { path: '/human-review', label: 'Action reviews', icon: FactCheckOutlined },
  { path: '/notifications', label: 'Activity & alerts', icon: HistoryOutlined },
  { path: '/users', label: 'Account & team', icon: PeopleOutline },
] as const

type CustomerShellProps = {
  session: PlatformAuthSessionSummary | null
  onSignOut: () => Promise<void>
  children: ReactNode
}

export function CustomerConsoleShell(props: CustomerShellProps) {
  const workspace = useDeploymentWorkspace()
  const customerId = props.session?.customerId ?? ''
  const customerSession = props.session?.role === 'CUSTOMER_ADMIN' && props.session.authenticationMode === 'SESSION' && Boolean(customerId)
  const portfolio = useQuery({ queryKey: ['customer-deployment-portfolio', customerId], queryFn: fetchCustomerDeploymentPortfolio, enabled: customerSession })
  const apps = useQuery({ queryKey: ['ai-workspace-installations', customerId], queryFn: () => fetchAIWorkspaceInstallations(customerId), enabled: customerSession })
  const expandedNavigation = Boolean(portfolio.data?.length || apps.data?.length || workspace.selectedDeploymentSummary || portfolio.isError || apps.isError)
  const unresolved = workspace.isScopedPage && !workspace.deploymentsLoading
    && workspace.unresolvedRequestedDeploymentId != null
  return <CustomerShellLayout {...props} buildWorkspacePath={workspace.buildWorkspacePath}
    deploymentName={workspace.selectedDeploymentSummary?.name} humanSupportAvailable={workspace.workspace?.conversationHandoverConfigured ?? false} expandedNavigation={expandedNavigation}>
    {unresolved ? <DeploymentWorkspaceUnresolvedState /> : props.children}
  </CustomerShellLayout>
}

export function CustomerShellLayout({ session, onSignOut, children, buildWorkspacePath, deploymentName, humanSupportAvailable = false, expandedNavigation = true }: CustomerShellProps & {
  buildWorkspacePath: (path: string) => string
  deploymentName?: string | null
  humanSupportAvailable?: boolean
  expandedNavigation?: boolean
}) {
  if (session?.role !== 'CUSTOMER_ADMIN' || session.authenticationMode !== 'SESSION') {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  return <CustomerConsoleTheme><CustomerShellContent session={session} onSignOut={onSignOut}
    buildWorkspacePath={buildWorkspacePath} deploymentName={deploymentName} humanSupportAvailable={humanSupportAvailable} expandedNavigation={expandedNavigation}>{children}</CustomerShellContent></CustomerConsoleTheme>
}

function CustomerShellContent({ session, onSignOut, children, buildWorkspacePath, deploymentName, humanSupportAvailable, expandedNavigation }: CustomerShellProps & {
  buildWorkspacePath: (path: string) => string
  deploymentName?: string | null
  humanSupportAvailable: boolean
  expandedNavigation: boolean
}) {
  const location = useLocation()
  const compact = useMediaQuery(useTheme().breakpoints.down('md'))
  const [menuOpen, setMenuOpen] = useState(false)
  const color = useCustomerColorPreference()
  const active = navigation.find(item => item.path === location.pathname)
  const accountName = session?.customerName ?? 'Customer account'
  const accountInitial = accountName.charAt(0).toUpperCase()
  const nav = <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', p: 1.75 }}>
    <Stack direction="row" spacing={1} alignItems="center" sx={{ px: 1, pt: 1, pb: 3 }}>
      <Box sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', display: 'grid', placeItems: 'center', borderRadius: 1, width: 29, height: 29 }}><HubOutlined fontSize="small" /></Box>
      <Typography sx={{ fontSize: 20, fontWeight: 700 }}>LoomAI</Typography>
    </Stack>
    <Stack direction="row" spacing={1} alignItems="center" sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.25, mb: 2.5 }}>
      <Avatar variant="rounded" sx={{ width: 28, height: 28, bgcolor: 'action.selected', color: 'text.secondary', fontSize: 12 }}>{accountInitial}</Avatar>
      <Box sx={{ minWidth: 0 }}><Typography sx={{ fontSize: 12, fontWeight: 600, overflowWrap: 'anywhere' }}>{accountName}</Typography>
        <Typography sx={{ fontSize: 10 }} color="text.secondary">Customer workspace</Typography></Box>
    </Stack>
    <List component="nav" aria-label="Customer navigation" disablePadding>
      {navigation.filter(item => (expandedNavigation || ['/home', '/deployments', '/ai-workspaces', '/users'].includes(item.path))
        && (item.path !== '/human-support' || humanSupportAvailable)).map(item => <ListItemButton key={item.path} component={Link}
        to={isDeploymentWorkspacePath(item.path) ? buildWorkspacePath(item.path) : item.path}
        selected={location.pathname === item.path} onClick={() => setMenuOpen(false)}
        sx={{ borderRadius: 1, mb: 0.5, px: 1.25, py: 0.75, minHeight: 37,
          '&.Mui-selected': { bgcolor: 'action.selected', color: 'primary.main' } }}>
        <ListItemIcon sx={{ minWidth: 29, color: 'inherit' }}><item.icon sx={{ fontSize: 18 }} /></ListItemIcon>
        <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 12, fontWeight: location.pathname === item.path ? 600 : 400 }} />
      </ListItemButton>)}
    </List>
    <Box sx={{ flexGrow: 1, minHeight: 24 }} /><Divider />
    <Stack direction="row" spacing={1} alignItems="center" sx={{ px: 0.75, pt: 2 }}>
      <Avatar sx={{ width: 28, height: 28, fontSize: 12, bgcolor: 'action.selected', color: 'text.secondary' }}>{session?.displayName?.charAt(0) ?? accountInitial}</Avatar>
      <Box sx={{ minWidth: 0 }}><Typography sx={{ fontSize: 11, fontWeight: 600, overflowWrap: 'anywhere' }}>{session?.displayName ?? 'Customer administrator'}</Typography><Typography color="text.secondary" sx={{ fontSize: 10 }}>Customer administrator</Typography></Box>
    </Stack>
  </Box>
  return <Box sx={{ display: 'flex', minHeight: '100vh' }}>
    <Drawer id="customer-navigation" variant={compact ? 'temporary' : 'permanent'} open={!compact || menuOpen}
      onClose={() => setMenuOpen(false)} sx={{ width: compact ? 0 : 205, flexShrink: 0,
        '& .MuiDrawer-paper': { width: 205, boxSizing: 'border-box', bgcolor: 'background.paper', backgroundImage: 'none' } }}>{nav}</Drawer>
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Toolbar component="header" sx={{ minHeight: '62px !important', bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', gap: 1.25, px: { xs: 1.5, md: 3.5 }, position: 'sticky', top: 0, zIndex: 5 }}>
        {compact ? <IconButton aria-label="Open customer navigation" aria-controls="customer-navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen(true)}><MenuRounded /></IconButton> : null}
        <Stack direction="row" alignItems="center" spacing={0.75} sx={{ flex: 1, minWidth: 0 }}>
          <Typography color="text.secondary" noWrap sx={{ fontSize: 12, maxWidth: { xs: 80, md: 220 } }}>{accountName}</Typography>
          <ChevronRightRounded sx={{ fontSize: 16, color: 'text.secondary' }} />
          <Typography noWrap sx={{ fontSize: 12 }}>{active?.label ?? 'Workspace'}</Typography>
          {deploymentName ? <Typography color="text.secondary" noWrap sx={{ display: { xs: 'none', lg: 'block' }, fontSize: 11, maxWidth: 250 }}> / {deploymentName}</Typography> : null}
        </Stack>
        <FormControl size="small" sx={{ width: { xs: 84, md: 110 }, flexShrink: 0 }}>
          <Select value={color.preference} inputProps={{ 'aria-label': 'Customer color theme' }}
            onChange={event => color.setPreference(event.target.value as CustomerColorPreference)} sx={{ fontSize: 11 }}>
            <MenuItem value="system">System</MenuItem><MenuItem value="light">Light</MenuItem><MenuItem value="dark">Dark</MenuItem>
          </Select>
        </FormControl>
        <Tooltip title="Sign out"><IconButton aria-label="Sign out" onClick={() => { void onSignOut() }}><LogoutRounded sx={{ fontSize: 19 }} /></IconButton></Tooltip>
      </Toolbar>
      <Box component="main" sx={{ p: { xs: 2, md: 3.5 }, minWidth: 0 }}>{children}</Box>
    </Box>
  </Box>
}
