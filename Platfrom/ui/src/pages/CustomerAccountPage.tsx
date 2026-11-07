import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom'
import { Alert, Avatar, Box, Button, Stack, Typography } from '@mui/material'
import BusinessOutlined from '@mui/icons-material/BusinessOutlined'
import PeopleOutline from '@mui/icons-material/PeopleOutline'
import LockOutlined from '@mui/icons-material/LockOutlined'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { AccountSecurityPanel } from '../account/AccountSecurityPanel'
import { CustomerStaffAccessPanel } from '../components/customer/CustomerStaffAccessPanel'
import { accountPath, accountSection } from '../account/accountNavigation'
import { CustomerTeamWorkspace } from '../account/CustomerTeamWorkspace'
import { memberInitials } from '../account/customerTeamModel'

export function CustomerAccountPage() {
  const { session, supabaseSession } = usePlatformAuth()
  const location = useLocation()
  if (!isCustomerSession(session)) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  const section = accountSection(location.pathname)
  if (location.pathname === '/users' || location.pathname === '/account' || !['profile', 'team', 'loomai-access', 'security'].includes(location.pathname.split('/')[2])) {
    return <Navigate to={accountPath('team', location.search)} replace />
  }
  const titles = {
    team: ['Team & access', 'Choose who can manage your workspace.'],
    profile: ['My account', 'Your identity and the workspace you manage.'],
    'loomai-access': ['LoomAI collaboration', 'Let the LoomAI team help configure your workspace.'],
    security: ['Sign-in & security', 'Manage how you access your Console account.'],
  }
  const title = titles[section]
  return <Stack gap={2.5} sx={{ height: { md: '100%' }, minHeight: 0 }}>
    <Box><Typography component="h1" fontSize={28} fontWeight={700}>{title[0]}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{title[1]}</Typography></Box>
    <Stack direction="row" gap={1} alignItems="center" sx={{ pb: 2, borderBottom: 1, borderColor: 'divider' }}><BusinessOutlined sx={{ fontSize: 19, color: 'text.secondary' }} />
      <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{session.customerName ?? 'Your workspace'} <Box component="span" sx={{ color: 'text.secondary' }}>· Workspace-wide access</Box></Typography>
    </Stack>
    {section === 'team' && <CustomerTeamWorkspace key={`${session.actorId}:${session.customerId}:${session.authenticationMode}`} session={session} />}
    {section === 'loomai-access' && <Box sx={{ overflow: 'auto', maxWidth: 980 }}><CustomerStaffAccessPanel workspacePresentation /></Box>}
    {section === 'profile' && <Stack gap={3} sx={{ maxWidth: 780 }}>
      <Stack direction="row" alignItems="center" gap={2}><Avatar sx={{ width: 56, height: 56, bgcolor: 'action.selected', color: 'text.primary' }}>{memberInitials(session.displayName ?? 'Account')}</Avatar>
        <Box><Typography component="h2" fontSize={22} fontWeight={600}>{session.displayName ?? 'Your account'}</Typography><Typography variant="body2" color="text.secondary">{supabaseSession?.user.email ?? 'Console account'}</Typography></Box></Stack>
      <Box component="dl" sx={{ m: 0 }}>{[['Workspace', session.customerName ?? session.customerId], ['Access', 'Workspace administrator'], ['Sign-in', session.authenticationMode === 'SUPABASE' ? 'Supabase identity' : 'Console session']].map(([label, value]) => <Stack key={label} direction={{ xs: 'column', sm: 'row' }} gap={1} sx={{ py: 2, borderBottom: 1, borderColor: 'divider' }}><Typography component="dt" variant="body2" color="text.secondary" sx={{ width: 150 }}>{label}</Typography><Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: 'anywhere' }}>{value}</Typography></Stack>)}</Box>
      <Stack direction="row" gap={1} flexWrap="wrap"><Button component={RouterLink} to={accountPath('team', location.search)} startIcon={<PeopleOutline />} variant="outlined">Manage team details</Button><Button component={RouterLink} to={accountPath('security', location.search)} startIcon={<LockOutlined />}>Sign-in & security</Button></Stack>
    </Stack>}
    {section === 'security' && <AccountSecurityPanel />}
  </Stack>
}
