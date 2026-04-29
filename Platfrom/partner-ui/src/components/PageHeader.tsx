import { Box, Breadcrumbs, Stack, Typography, type SxProps, type Theme } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'

interface Crumb {
  label: string
  to?: string
}

export function PageHeader({
  title,
  subtitle,
  actions,
  breadcrumbs,
  sx,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
  breadcrumbs?: Crumb[]
  sx?: SxProps<Theme>
}) {
  return (
    <Box sx={{ mb: 3, ...sx }}>
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <Breadcrumbs sx={{ mb: 1 }} aria-label="breadcrumb">
          {breadcrumbs.map((crumb) =>
            crumb.to ? (
              <Typography
                key={crumb.label}
                component={RouterLink}
                to={crumb.to}
                color="text.secondary"
                sx={{ textDecoration: 'none', fontSize: '0.8125rem' }}
              >
                {crumb.label}
              </Typography>
            ) : (
              <Typography key={crumb.label} color="text.primary" sx={{ fontSize: '0.8125rem' }}>
                {crumb.label}
              </Typography>
            ),
          )}
        </Breadcrumbs>
      ) : null}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'flex-start' }} justifyContent="space-between">
        <Box>
          <Typography variant="h1">{title}</Typography>
          {subtitle ? (
            <Typography color="text.secondary" sx={{ mt: 0.75, maxWidth: 760 }}>
              {subtitle}
            </Typography>
          ) : null}
        </Box>
        {actions ? <Stack direction="row" spacing={1} flexWrap="wrap">{actions}</Stack> : null}
      </Stack>
    </Box>
  )
}
