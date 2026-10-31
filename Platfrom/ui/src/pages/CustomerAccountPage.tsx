import { Alert, Box, Button, Divider, Stack, Typography } from '@mui/material'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { UsersPage } from './UsersPage'

export function CustomerAccountPage() {
  const { session, supabaseSession } = usePlatformAuth()
  if (!isCustomerSession(session)) {
    return <Alert severity="error">A customer administrator session is required.</Alert>
  }
  return <Stack spacing={3}>
    <Box><Typography variant="h4">Account &amp; team</Typography><Typography color="text.secondary" variant="body2" sx={{ mt: 1 }}>Your business account and customer-scoped administrator access.</Typography></Box>
    <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3,minmax(0,1fr))' }, gap: 3 }}>
      {[
        ['Account', session.customerName ?? session.customerId],
        ['Signed in as', session.displayName ?? session.actorId ?? 'Customer administrator'],
        ['Access', 'Customer administrator'],
      ].map(([label,value]) => <Box key={label}><Typography component="dt" variant="caption" color="text.secondary">{label}</Typography><Typography component="dd" sx={{ m: 0, mt: 0.5, overflowWrap: 'anywhere' }}>{value}</Typography></Box>)}
    </Box>
    {supabaseSession && <Box><Button variant="outlined" href="/auth/recovery">Change password</Button></Box>}
    <Divider />
    {session.canManageUserDirectory ? <UsersPage embedded /> : <Alert severity="info">Team administration is not enabled for this account. Your own sign-in and deployment access are unchanged.</Alert>}
  </Stack>
}
