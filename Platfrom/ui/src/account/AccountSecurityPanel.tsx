import { useCallback, useState } from 'react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import { Alert, Box, Button, Dialog, DialogContent, DialogTitle, Divider, IconButton, Stack, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'
import { useAccountEditor } from './AccountEditorContext'
import { accountPath } from './accountNavigation'

export function AccountSecurityPanel() {
  const { session, supabaseSession, customerConfig } = usePlatformAuth()
  const location = useLocation()
  const [emailLink, setEmailLink] = useState(false)
  const [busy, setBusy] = useState(false)
  const discard = useCallback(() => setEmailLink(false), [])
  useAccountEditor(false, busy, discard)
  const email = supabaseSession?.user.email ?? session?.actorId ?? ''
  return <Stack gap={3} sx={{ maxWidth: 780 }}>
    <Box><Typography component="h2" fontSize={18} fontWeight={600}>Your sign-in</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{session?.authenticationMode === 'SUPABASE' ? 'You are signed in with your linked Supabase identity.' : 'You are signed in with a Console account.'}</Typography>
      {email && <Typography variant="body2" sx={{ mt: 1, overflowWrap: 'anywhere' }}>{email}</Typography>}</Box><Divider />
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} alignItems={{ sm: 'center' }}><Box><Typography fontWeight={600} variant="body2">Password</Typography><Typography variant="body2" color="text.secondary">{supabaseSession ? 'Change your own password through the secure recovery flow.' : 'Reset your Console password from your own member details.'}</Typography></Box>
      {supabaseSession ? <Button component={RouterLink} to="/auth/recovery" variant="outlined">Change password</Button> : session?.canManageUserDirectory ? <Button component={RouterLink} to={accountPath('team', location.search)} variant="outlined">Manage Console password</Button> : <Typography variant="body2" color="text.secondary">Contact your workspace administrator.</Typography>}</Stack>
    {customerConfig?.enabled && email && <><Divider /><Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} alignItems={{ sm: 'center' }}><Box><Typography fontWeight={600} variant="body2">Sign in with an email link</Typography><Typography variant="body2" color="text.secondary">Request a one-time link for your existing customer identity.</Typography></Box><Button variant="outlined" onClick={() => setEmailLink(true)}>Request sign-in link</Button></Stack></>}
    <Alert severity="info">Team password resets affect Console credentials only. Linked Supabase passwords are managed through the account owner's recovery flow.</Alert>
    <Dialog open={emailLink} onClose={() => !busy && setEmailLink(false)} fullWidth maxWidth="xs" aria-label="Email sign-in link"><DialogTitle><Stack direction="row" alignItems="center" justifyContent="space-between">Email sign-in link<IconButton aria-label="Close email sign-in" disabled={busy} onClick={() => setEmailLink(false)}><CloseRounded /></IconButton></Stack></DialogTitle>
      <DialogContent><SupabaseIdentityForm emailLinkAddress={email} onBusyChange={setBusy} /></DialogContent></Dialog>
  </Stack>
}
