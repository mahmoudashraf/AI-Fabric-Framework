import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { type FormEvent, useState } from 'react'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'

export function CustomerAuthRecoveryPage() {
  const auth = usePlatformAuth()
  const [password, setPassword] = useState(''), [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false), [completed, setCompleted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      if (!password || password !== confirmation) throw new Error('Enter the same new password in both fields.')
      if (!auth.supabase || !auth.supabaseSession) throw new Error('Open a valid password recovery email first.')
      const result = await auth.supabase.auth.updateUser({ password })
      if (result.error) throw result.error
      await auth.signOut()
      setPassword(''); setConfirmation(''); setCompleted(true)
    } catch (ex) { setError(ex instanceof Error ? ex.message : 'Password recovery failed.') }
    finally { setBusy(false) }
  }
  return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
    <Paper variant="outlined" sx={{ width: '100%', maxWidth: 480, p: 4 }}><Stack component="form" spacing={3} onSubmit={submit}>
      <Typography variant="h5">Choose a new password</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {completed ? <><Alert severity="success">Password updated. Sign in with your new password.</Alert><Button href="/">Return to sign in</Button></>
        : <>
          {!auth.customerAuthLoading && !auth.supabaseSession && <Alert severity="warning">Open your recovery email to continue.</Alert>}
          <TextField required autoComplete="new-password" label="New password" type="password" value={password} onChange={event => setPassword(event.target.value)} />
          <TextField required autoComplete="new-password" label="Confirm new password" type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} />
          <Button type="submit" variant="contained" disabled={busy || !auth.supabaseSession}>Update password</Button>
        </>}
    </Stack></Paper>
  </Box>
}
