import { Box } from '@mui/material'
import type { ReactNode } from 'react'

export function VisuallyHidden({ children }: { children: ReactNode }) {
  return (
    <Box
      component="span"
      sx={{
        border: 0,
        clip: 'rect(0 0 0 0)',
        height: 1,
        margin: -1,
        overflow: 'hidden',
        padding: 0,
        position: 'absolute',
        whiteSpace: 'nowrap',
        width: 1,
      }}
    >
      {children}
    </Box>
  )
}
