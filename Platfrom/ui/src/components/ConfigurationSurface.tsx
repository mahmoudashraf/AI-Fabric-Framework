import { useMemo, type ReactNode } from 'react'
import { Accordion, AccordionDetails, AccordionSummary, Box, ThemeProvider, Typography, useTheme } from '@mui/material'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { createConsoleTheme } from '../theme/consoleTheme'

/** Local to customer-accessible editors; unrelated staff screens retain their theme. */
export function ConfigurationSurface({ children, enabled = true }: { children: ReactNode; enabled?: boolean }) {
  const outer = useTheme()
  const theme = useMemo(() => createConsoleTheme(outer.palette.mode), [outer.palette.mode])
  if (!enabled) return <>{children}</>
  return <ThemeProvider theme={theme}>
    <Box className="configuration-surface" sx={{ minWidth: 0, color: 'text.primary',
      '& .MuiStack-root, & .MuiGrid-item': { minWidth: 0, maxWidth: '100%' },
      '& .MuiGrid-container': { maxWidth: '100%' },
      '@media (max-width: 599.95px)': {
        '& .MuiGrid-container': { width: '100%', marginLeft: 0 },
        '& .MuiGrid-item': { paddingLeft: 0 },
      },
      '& .MuiTypography-root': { letterSpacing: 0, overflowWrap: 'anywhere' },
      '& .MuiTypography-h4': { fontSize: 23, fontWeight: 650 },
      '& .MuiTypography-h6': { fontSize: 16, fontWeight: 600 },
      '& > .MuiStack-root > .MuiCard-root': {
        background: 'none', border: 0, borderBottom: 1, borderColor: 'divider', borderRadius: 0,
        '& > .MuiCardContent-root': { px: 0, pt: 0, pb: 3 },
      },
      '& .MuiCard-root .MuiCard-root': { boxShadow: 'none', backgroundImage: 'none' },
      '& .MuiAccordion-root': { borderRadius: '6px !important', overflow: 'hidden' },
      '& .MuiAccordionSummary-root': { minHeight: 48 },
      '& .MuiAccordionSummary-content': { my: 1.5 },
      '& .MuiAccordionDetails-root': { pt: 1, pb: 2.5 },
      '& .MuiTableContainer-root': { maxWidth: '100%', overflowX: 'auto' },
      '& .MuiCardContent-root, & .MuiAccordionDetails-root': { minWidth: 0, maxWidth: '100%', overflowX: 'auto' },
      '& .MuiTable-root': { minWidth: 500 },
      '& pre': { maxWidth: '100%', overflowX: 'auto' },
    }}>{children}</Box>
  </ThemeProvider>
}

export function ConfigurationDetails({ children, label }: { children: ReactNode; label: string }) {
  return <Accordion disableGutters sx={{ border: 1, borderColor: 'divider' }}>
    <AccordionSummary expandIcon={<ExpandMoreRounded />}><Typography variant="subtitle2">{label}</Typography></AccordionSummary>
    <AccordionDetails>{children}</AccordionDetails>
  </Accordion>
}
