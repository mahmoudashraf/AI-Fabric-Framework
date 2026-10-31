import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { readEmailLink } from '../auth/emailLinkVerification'
import { emailCallbackError } from '../auth/identityNavigation'
import { CustomerEntryLayout } from './CustomerOnboardingPage'

export function CustomerAccountRecoveryPage({ confirmation = false }: { confirmation?: boolean }) {
  const auth = usePlatformAuth()
  const [input] = useState(() => {
    if (!confirmation) return { tokenHash: null, error: null }
    try {
      const error = emailCallbackError(window.location.search, window.location.hash)
      const link = readEmailLink(window.location.search, 'email')
      return { tokenHash: error ? null : link?.tokenHash ?? null, error: error || (!link ? 'Open your newest recovery email, or request another link.' : null) }
    } catch { return { tokenHash: null, error: 'This recovery link is invalid. Request another link.' } }
  })
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempted, setAttempted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  useEffect(() => {
    if (!confirmation) return
    // Keep the one-use credential in memory, not history, storage or outgoing referrers.
    window.history.replaceState(null, '', '/auth/account-recovery')
  }, [confirmation])
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setMessage(null)
    try {
      if (!auth.supabase) throw new Error('Account recovery is temporarily unavailable.')
      if (confirmation) {
        if (!input.tokenHash || attempted) throw new Error('Request a new recovery link before trying again.')
        setAttempted(true)
        await auth.recoverCustomer(input.tokenHash, email.trim())
        window.location.replace('/home')
      } else {
        const result = await auth.supabase.auth.signInWithOtp({ email: email.trim(), options: {
          shouldCreateUser: false, emailRedirectTo: window.location.origin + '/auth/account-recovery',
        } })
        if (result.error) throw result.error
        setMessage('If this email has a LoomAI account, a one-use recovery link has been requested. Open the newest email to continue. Check spam too.')
      }
    } catch (failure) {
      const unknownAccount = !confirmation && failure && typeof failure === 'object' && 'code' in failure && failure.code === 'otp_disabled'
      setError(unknownAccount ? 'Create your LoomAI sign-in account first, then return here to recover your existing workspace.'
        : failure instanceof Error ? failure.message : 'Recovery could not be completed.')
    } finally { setBusy(false) }
  }
  return <CustomerEntryLayout>
    <Box><Typography variant="h4" component="h1">{confirmation ? 'Recover your workspace' : 'Recover your Console access'}</Typography>
      <Typography color="text.secondary" sx={{ mt: 1 }}>{confirmation
        ? 'Confirm your email to connect this verified identity to your existing customer workspace.'
        : 'Keep your existing workspace, products and permissions. No old Console password is needed.'}</Typography></Box>
    {input.error && <Alert severity="warning">{input.error}</Alert>}
    {error && <Alert severity="error">{error}</Alert>}
    {message && <Alert severity="success">{message}</Alert>}
    {(!confirmation || input.tokenHash) && <Stack component="form" spacing={2.5} onSubmit={submit}>
      <TextField label={confirmation ? 'Confirm your email' : 'Email'} required type="email" autoComplete="username"
        value={email} onChange={event => setEmail(event.target.value)} disabled={busy || attempted}
        slotProps={{ htmlInput: { maxLength: 255 } }} />
      {confirmation && <Typography variant="body2" color="text.secondary">This connects your customer account only. It does not create a workspace, change your password or grant staff access.</Typography>}
      <Button type="submit" variant="contained" disabled={busy || attempted || !auth.supabase || !email.trim()}>
        {busy ? 'Please wait...' : confirmation ? 'Recover my workspace' : 'Email me a recovery link'}</Button>
    </Stack>}
    {confirmation && <Button component={Link} to="/recover-account">Request a new recovery link</Button>}
    <Button component={Link} to="/sign-in">Return to sign in</Button>
    {!confirmation && <Button component={Link} to="/auth/recovery">Reset my LoomAI sign-in password</Button>}
  </CustomerEntryLayout>
}
