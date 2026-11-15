import CheckRoundedIcon from '@mui/icons-material/CheckRounded'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import ExtensionRoundedIcon from '@mui/icons-material/ExtensionRounded'
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded'
import { Box, Chip, Link, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { capabilityAccess, capabilityDraftStatus, type Capability } from './capabilityPresentation'

export function CapabilitiesTable({ rows, href }: { rows: Capability[]; href: (section: string) => string }) {
  return <Box sx={{ border: 1, borderColor: 'divider', borderRadius: '6px', overflow: 'hidden', flexShrink: 0 }}>
    <Table size="small" aria-label="Your capabilities" sx={{ tableLayout: 'fixed',
      '& th, & td': { px: { xs: 1, sm: 1.5 }, py: 1.25, overflowWrap: 'anywhere' },
      '& th': { bgcolor: 'action.hover', fontWeight: 600 },
      '& tbody tr:last-child td, & tbody tr:last-child th': { borderBottom: 0 },
    }}>
      <TableHead><TableRow>
        <TableCell sx={{ width: { xs: '30%', sm: '42%' } }}>Customer outcome</TableCell>
        <TableCell sx={{ width: { xs: '36%', sm: '30%' } }}>Access</TableCell>
        <TableCell sx={{ width: { xs: '34%', sm: '28%' } }}>Draft status</TableCell>
      </TableRow></TableHead>
      <TableBody>{rows.map(row => {
        const access = capabilityAccess(row.action)
        const status = capabilityDraftStatus(row)
        return <TableRow key={row.key} hover>
          <TableCell component="th" scope="row" sx={{ '&.MuiTableCell-root': { bgcolor: 'transparent' } }}>
            <Stack direction="row" gap={1} alignItems="center">
              {row.kind === 'knowledge' ? <DescriptionOutlinedIcon color="action" fontSize="small" sx={{ display: { xs: 'none', sm: 'block' }, flexShrink: 0 }} />
                : <ExtensionRoundedIcon color="action" fontSize="small" sx={{ display: { xs: 'none', sm: 'block' }, flexShrink: 0 }} />}
              <Link component={RouterLink} to={href(`capabilities/${encodeURIComponent(row.key)}`)} underline="hover" color="text.primary" sx={{ fontWeight: 600, minWidth: 0 }}>{row.label}</Link>
            </Stack>
          </TableCell>
          <TableCell><Typography variant="body2">{access.signIn}</Typography>
            {row.action?.requiresConfirmation === true ? <Typography variant="caption" color="text.secondary">{access.confirmation}</Typography> : null}
          </TableCell>
          <TableCell><Chip size="small" variant="outlined" color={status.attention ? 'warning' : 'success'}
            icon={status.attention ? <WarningAmberRoundedIcon /> : <CheckRoundedIcon />} label={status.label}
            sx={{ height: 'auto', minHeight: 24, maxWidth: '100%', borderRadius: '6px', borderWidth: { xs: 0, sm: 1 }, py: .25, fontSize: 12,
              '& .MuiChip-label': { whiteSpace: 'normal', overflowWrap: 'normal', textAlign: 'left', px: { xs: 0, sm: .75 } },
              '& .MuiChip-icon': { display: { xs: 'none', sm: 'block' }, fontSize: 16, ml: .5, flexShrink: 0 } }} /></TableCell>
        </TableRow>
      })}</TableBody>
    </Table>
  </Box>
}
