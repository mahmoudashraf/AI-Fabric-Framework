import { Chip } from '@mui/material'
import { titleize } from '../utils/format'

export function TierBadge({ tier }: { tier: string }) {
  const normalized = tier.toUpperCase()
  const color =
    normalized === 'FREE'
      ? { bg: '#F1F5F9', fg: '#334155' }
      : normalized === 'STARTER'
        ? { bg: '#EFF6FF', fg: '#1E40AF' }
        : { bg: '#F5F3FF', fg: '#5B21B6' }
  return (
    <Chip
      size="small"
      label={titleize(tier)}
      sx={{
        bgcolor: color.bg,
        color: color.fg,
        fontWeight: 700,
        border: '1px solid',
        borderColor: 'divider',
      }}
    />
  )
}
