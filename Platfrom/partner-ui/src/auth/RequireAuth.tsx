import { Box, CircularProgress, Alert } from '@mui/material'
import { Navigate, useLocation } from 'react-router-dom'
import { useSupabaseAuth } from './SupabaseProvider'

export function RequireAuth({ children }: { children: JSX.Element }) {
  const { configured, loading, session } = useSupabaseAuth()
  const location = useLocation()

  if (!configured) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3 }}>
        <Alert severity="warning" sx={{ maxWidth: 520 }}>
          Partner authentication is not configured. Set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY for this environment.
        </Alert>
      </Box>
    )
  }

  if (loading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress size={28} />
      </Box>
    )
  }

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return children
}
