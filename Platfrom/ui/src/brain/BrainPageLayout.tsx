import type { ReactNode } from 'react'
import { Box, Stack, Typography } from '@mui/material'

export function BrainPageHeader({ title, description, action, stackOnMobile = false }: { title: string; description?: string; action?: ReactNode; stackOnMobile?: boolean }) {
  return <Stack direction={stackOnMobile ? { xs: 'column', sm: 'row' } : 'row'} gap={2} alignItems="flex-start" justifyContent="space-between" sx={{ mb: 2.5, flexShrink: 0 }}>
    <Box sx={{ minWidth: 0 }}><Typography component="h1" sx={{ fontSize: 26, lineHeight: 1.25, fontWeight: 650 }}>{title}</Typography>
      {description && <Typography color="text.secondary" variant="body2" sx={{ mt: 0.75 }}>{description}</Typography>}</Box>{action}
  </Stack>
}

export const brainPaneSx = { minWidth: 0, minHeight: 0, overflow: 'auto', overscrollBehavior: 'contain' } as const
