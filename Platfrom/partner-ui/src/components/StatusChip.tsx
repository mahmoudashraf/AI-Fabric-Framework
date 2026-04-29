import { Chip } from '@mui/material'
import { statusFromWire, statusTokens, type PartnerStatus } from '../theme/statusTokens'

export function StatusChip({ status, label }: { status: string | PartnerStatus | undefined; label?: string }) {
  const tokenKey = statusFromWire(status)
  const token = statusTokens[tokenKey]
  const Icon = token.icon
  return (
    <Chip
      size="small"
      icon={<Icon sx={{ color: `${token.fg} !important`, fontSize: 16 }} />}
      label={label ?? token.label}
      sx={{
        bgcolor: token.bg,
        color: token.fg,
        border: `1px solid ${token.main}33`,
        fontWeight: 700,
        '& .MuiChip-label': { px: 1 },
      }}
    />
  )
}
