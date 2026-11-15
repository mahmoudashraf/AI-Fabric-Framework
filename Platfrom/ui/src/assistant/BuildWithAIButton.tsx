import { forwardRef } from 'react'
import { Button, type ButtonProps } from '@mui/material'
import { alpha, type Theme } from '@mui/material/styles'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'

export const BuildWithAIButton = forwardRef<HTMLButtonElement, ButtonProps>(function BuildWithAIButton({ sx, children = 'Build with AI', ...props }, ref) {
  return <Button ref={ref} variant="contained" startIcon={<AutoAwesomeOutlined />} {...props} sx={[
    (theme: Theme) => ({
      minHeight: 38, px: 2, fontWeight: 700, borderRadius: '6px', whiteSpace: 'nowrap',
      animation: 'buildAiInvite 1.8s ease-out 3',
      transition: 'transform 160ms ease, box-shadow 160ms ease',
      '&:hover': { transform: 'translateY(-1px)', boxShadow: theme.shadows[4] },
      '& .MuiButton-startIcon': { animation: 'buildAiSpark 1.8s ease-in-out 3' },
      '&:focus-visible': { outline: `2px solid ${theme.palette.primary.main}`, outlineOffset: 3 },
      '@keyframes buildAiInvite': {
        '0%, 100%': { boxShadow: `0 0 0 0 ${alpha(theme.palette.primary.main, 0)}` },
        '35%': { boxShadow: `0 0 0 4px ${alpha(theme.palette.primary.main, .22)}` },
        '75%': { boxShadow: `0 0 0 8px ${alpha(theme.palette.primary.main, 0)}` },
      },
      '@keyframes buildAiSpark': {
        '0%, 100%': { transform: 'rotate(0deg) scale(1)' },
        '40%': { transform: 'rotate(-10deg) scale(1.14)' },
        '65%': { transform: 'rotate(5deg) scale(1.06)' },
      },
      '@media (prefers-reduced-motion: reduce)': {
        animation: 'none', transition: 'none',
        '& .MuiButton-startIcon': { animation: 'none' },
        '&:hover': { transform: 'none' },
      },
    }),
    ...(Array.isArray(sx) ? sx : [sx]),
  ]}>{children}</Button>
})
