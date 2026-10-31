import { Alert, Button, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { type FormEvent, useState } from 'react'
import { prepareCustomerIdentity, usePlatformAuth } from './PlatformAuthProvider'
import { identityEmailRedirect, rememberCustomerAuthReturn } from './customerAuthReturn'
import type { IdentityMethod } from './identityNavigation'

const submitLabels: Record<IdentityMethod, string> = {
  login: 'Sign in', register: 'Create account', email: 'Send sign-in link',
  recovery: 'Send password reset link', verify: 'Resend verification email',
}

export function SupabaseIdentityForm({ linking = false, allowRegistration = false, initialMode = 'login' }: { linking?: boolean; allowRegistration?: boolean; initialMode?: IdentityMethod }) {
  const auth = usePlatformAuth()
  const [mode, setMode] = useState<IdentityMethod>(initialMode)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const passwordRequired = mode === 'login' || mode === 'register'
  const changeMode = (next: IdentityMethod) => { setMode(next); setError(null); setMessage(null); setPassword('') }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setMessage(null)
    try {
      if (!auth.supabase) throw new Error('Customer sign-in is not configured.')
      const address = email.trim()
      prepareCustomerIdentity(linking)
      rememberCustomerAuthReturn(linking)
      if (mode === 'register') {
        const { error } = await auth.supabase.auth.signUp({ email: address, password, options: { emailRedirectTo: identityEmailRedirect() } })
        if (error) throw error
        setMessage('If your account needs verification, check your email for the link. Already verified? Sign in or request a sign-in link.')
      } else if (mode === 'email') {
        const { error } = await auth.supabase.auth.signInWithOtp({ email: address,
          options: { shouldCreateUser: false, emailRedirectTo: identityEmailRedirect() } })
        if (error) throw error
        setMessage('If this email has an account, a sign-in link has been requested. Open the newest email in this browser. Check spam too.')
      } else if (mode === 'recovery') {
        const { error } = await auth.supabase.auth.resetPasswordForEmail(address, { redirectTo: identityEmailRedirect(true) })
        if (error) throw error
        setMessage('If this email has an account, a password reset link has been requested. Open the newest email in this browser. Check spam too.')
      } else if (mode === 'verify') {
        const { error } = await auth.supabase.auth.resend({ type: 'signup', email: address, options: { emailRedirectTo: identityEmailRedirect() } })
        if (error) throw error
        setMessage('If this account still needs verification, a new email has been requested. Already verified accounts can sign in or request a sign-in link.')
      } else if (linking) {
        const { error } = await auth.supabase.auth.signInWithPassword({ email: address, password })
        if (error) throw error
      } else {
        await auth.signInCustomer(address, password)
      }
      setPassword('')
    } catch (ex) { setError(ex instanceof Error ? ex.message : 'Sign-in failed.') }
    finally { setBusy(false) }
  }
  return <Stack component="form" spacing={2} onSubmit={submit}>
    <ToggleButtonGroup exclusive value={mode === 'register' ? 'register' : 'login'} onChange={(_, next) => { if (next) changeMode(next) }} disabled={busy} size="small" fullWidth>
      <ToggleButton value="login">Sign in</ToggleButton>{allowRegistration && <ToggleButton value="register">Create account</ToggleButton>}
    </ToggleButtonGroup>
    {error && <Alert severity="error">{error}</Alert>}
    {message && <Alert severity="success">{message}</Alert>}
    <TextField required autoComplete="username" type="email" label="Email" value={email} disabled={busy} onChange={event => setEmail(event.target.value)} />
    {passwordRequired && <TextField required autoComplete={mode === 'register' ? 'new-password' : 'current-password'} type="password" label="Password" value={password} disabled={busy} onChange={event => setPassword(event.target.value)} />}
    <Button type="submit" variant="contained" disabled={busy || !auth.supabase}>{busy ? 'Please wait...' : submitLabels[mode]}</Button>
    <Stack spacing={0.5}>
      {mode !== 'email' && <Button disabled={busy} onClick={() => changeMode('email')}>Email me a sign-in link</Button>}
      {mode !== 'recovery' && <Button disabled={busy} onClick={() => changeMode('recovery')}>Forgot password?</Button>}
      {mode !== 'verify' && <Button disabled={busy} onClick={() => changeMode('verify')}>Resend verification email</Button>}
      {!passwordRequired && <Button disabled={busy} onClick={() => changeMode('login')}>Use password instead</Button>}
    </Stack>
    {allowRegistration && <Typography variant="caption" color="text.secondary">One LoomAI account. Access to each workspace is assigned separately.</Typography>}
  </Stack>
}
