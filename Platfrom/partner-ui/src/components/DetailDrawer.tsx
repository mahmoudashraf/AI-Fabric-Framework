import { Box, Drawer, IconButton, Stack, Typography } from '@mui/material'
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined'
import type { ReactNode } from 'react'

export function DetailDrawer({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}) {
  return (
    <Drawer anchor="right" open={open} onClose={onClose} PaperProps={{ sx: { width: 'min(560px, 100vw)' } }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h2">{title}</Typography>
        <IconButton onClick={onClose} aria-label="Close detail drawer">
          <CloseOutlinedIcon />
        </IconButton>
      </Stack>
      <Box sx={{ p: 3 }}>{children}</Box>
    </Drawer>
  )
}
