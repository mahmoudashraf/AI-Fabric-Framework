import { Alert, Button, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { type FormEvent, useState } from 'react'
import { usePlatformAuth } from './PlatformAuthProvider'
import { rememberCustomerAuthReturn } from './customerAuthReturn'

export function SupabaseIdentityForm({ linking = false, allowRegistration = false }: { linking?: boolean; allowRegistration?: boolean }) {
  const auth = usePlatformAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setMessage(null)
    try {
      if (!auth.supabase) throw new Error('Customer sign-in is not configured.')
      if (mode === 'register') {
        rememberCustomerAuthReturn(linking)
        const { error } = await auth.supabase.auth.signUp({ email, password, options: { emailRedirectTo: `${window.location.origin}/auth/callback` } })
        if (error) throw error
        setMessage('Check your email to verify your account. Then return here to sign in.')
      } else if (linking) {
        const { error } = await auth.supabase.auth.signInWithPassword({ email, password })
        if (error) throw error
      } else await auth.signInCustomer(email, password)
      setPassword('')
    } catch (ex) { setError(ex instanceof Error ? ex.message : 'Sign-in failed.') }
    finally { setBusy(false) }
  }
  return <Stack component="form" spacing={2} onSubmit={submit}>
    {allowRegistration && <ToggleButtonGroup exclusive value={mode} onChange={(_, next) => { if (next) setMode(next) }} size="small" fullWidth>
      <ToggleButton value="login">Sign in</ToggleButton><ToggleButton value="register">Create account</ToggleButton>
    </ToggleButtonGroup>}
    {error && <Alert severity="error">{error}</Alert>}
    {message && <Alert severity="success">{message}</Alert>}
    <TextField required autoComplete="username" type="email" label="Email" value={email} onChange={event => setEmail(event.target.value)} />
    <TextField required autoComplete={mode === 'register' ? 'new-password' : 'current-password'} type="password" label="Password" value={password} onChange={event => setPassword(event.target.value)} />
    <Button type="submit" variant="contained" disabled={busy || !auth.supabase}>{busy ? 'Please wait...' : mode === 'register' ? 'Create account' : 'Sign in'}</Button>
    {mode === 'login' && <Button disabled={busy || !email || !auth.supabase} onClick={async () => {
      setBusy(true); setError(null); setMessage(null)
      try {
        const result = await auth.supabase!.auth.resetPasswordForEmail(email, { redirectTo: `${window.location.origin}/auth/recovery` })
        if (result.error) throw result.error
        setMessage('Check your email for the password recovery link.')
      } catch (ex) { setError(ex instanceof Error ? ex.message : 'Password recovery failed.') }
      finally { setBusy(false) }
    }}>Forgot password?</Button>}
    {allowRegistration && <Typography variant="caption" color="text.secondary">An account alone does not grant access to customer deployments.</Typography>}
  </Stack>
}
