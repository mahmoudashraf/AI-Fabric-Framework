import { Link } from 'react-router-dom'
import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { deskSignInUrl } from '../auth/identityNavigation'
import { CustomerConsoleTheme } from '../theme/CustomerConsoleTheme'

export function SupportTeamIntakePage() {
  const auth = usePlatformAuth()
  let target: string | null = null
  try { target = deskSignInUrl(auth.customerConfig) } catch { /* No guessed or unregistered Desk destination. */ }
  return <CustomerConsoleTheme><Box sx={{ minHeight: '100vh', bgcolor: 'background.default', color: 'text.primary', p: { xs: 3, md: 5 } }}>
    <Stack spacing={2.5} sx={{ maxWidth: 640, mx: 'auto', py: 5 }}>
      <Typography variant="overline" color="text.secondary">LoomAI support team</Typography>
      <Typography variant="h4">LoomAI Desk</Typography>
      <Typography color="text.secondary">Your agent permissions are checked in Desk.</Typography>
      {auth.customerAuthLoading ? <CircularProgress size={24} /> : target
        ? <Button component="a" href={target} variant="contained" endIcon={<OpenInNewRounded />} sx={{ alignSelf: 'flex-start' }}>Open LoomAI Desk</Button>
        : <Alert severity="warning">Desk sign-in is not configured. Contact the workspace owner.</Alert>}
      <Stack direction="row" spacing={1}>
        {isCustomerSession(auth.session) && <Button component={Link} to="/support">Back to support</Button>}
        <Button component={Link} to="/">LoomAI products</Button>
      </Stack>
    </Stack>
  </Box></CustomerConsoleTheme>
}
