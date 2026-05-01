import { Chip, Stack, Typography } from '@mui/material'
import FolderZipOutlinedIcon from '@mui/icons-material/FolderZipOutlined'

export function EvidenceAttachment({ items }: { items: string[] }) {
  if (items.length === 0) {
    return <Typography color="text.secondary">No evidence attachments on this reply.</Typography>
  }
  return (
    <Stack direction="row" spacing={1} flexWrap="wrap">
      {items.map((item) => (
        <Chip key={item} icon={<FolderZipOutlinedIcon />} label={item} size="small" variant="outlined" />
      ))}
    </Stack>
  )
}
