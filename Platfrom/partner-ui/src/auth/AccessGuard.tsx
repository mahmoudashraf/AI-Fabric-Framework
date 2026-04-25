import { Alert, Box } from '@mui/material'

export function AccessGuard({
  hasAccess,
  children,
  message,
}: {
  hasAccess: boolean
  children: JSX.Element
  message: string
}) {
  if (!hasAccess) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="warning">{message}</Alert>
      </Box>
    )
  }
  return children
}
