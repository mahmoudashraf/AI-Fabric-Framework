import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import type { SvgIconComponent } from '@mui/icons-material'

export function EmptyState({
  icon: Icon,
  title,
  body,
  actionLabel,
  onAction,
}: {
  icon: SvgIconComponent
  title: string
  body: string
  actionLabel?: string
  onAction?: () => void
}) {
  return (
    <Paper sx={{ p: 3 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <Box sx={{ p: 1, borderRadius: 2, bgcolor: 'action.hover', display: 'grid', placeItems: 'center' }}>
            <Icon fontSize="small" color="primary" />
          </Box>
          <Box>
            <Typography variant="h3">{title}</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5, maxWidth: 640 }}>
              {body}
            </Typography>
          </Box>
        </Stack>
        {actionLabel && onAction ? (
          <Button variant="contained" onClick={onAction}>
            {actionLabel}
          </Button>
        ) : null}
      </Stack>
    </Paper>
  )
}
