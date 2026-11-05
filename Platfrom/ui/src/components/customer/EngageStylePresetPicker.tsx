import { useId, useState } from 'react'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Radio, RadioGroup, Stack, Tooltip, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import PaletteOutlined from '@mui/icons-material/PaletteOutlined'
import SendRounded from '@mui/icons-material/SendRounded'
import { WORKSPACE_STYLE_PRESETS, resolveWorkspaceStylePreset, type WorkspaceStylePresetId } from '../../../../../max-mode-widget/src/stylePresets'

type StylePreset = (typeof WORKSPACE_STYLE_PRESETS)[number]

/** A small visual specimen, not a second workspace renderer or capability preview. */
function StyleThumbnail({ preset }: { preset: StylePreset }) {
  const { surface, accent, text: ink } = preset[preset.defaultScheme]
  const radius = preset.radius
  return <Box aria-hidden="true" data-style-thumbnail={preset.id} sx={{ height: 82, width: '100%', p: 1.25, overflow: 'hidden',
    bgcolor: surface, color: ink, borderRadius: '4px', border: '1px solid', borderColor: preset.id === 'pop' ? ink : `${ink}25`,
    fontFamily: preset.displayFont, display: 'grid', gridTemplateRows: '18px 1fr 17px', gap: 0.75 }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between">
      <Box component="span" sx={{ fontSize: 17, lineHeight: 1, fontWeight: 700 }}>Aa</Box>
      <Stack direction="row" spacing={0.4}>{preset.swatches.slice(1).map((color, index) =>
        <Box key={`${color}:${index}`} sx={{ width: 6, height: 6, bgcolor: color, borderRadius: '50%', border: `1px solid ${ink}30` }} />)}</Stack>
    </Stack>
    <Stack spacing={0.5}>
      <Box sx={{ alignSelf: 'flex-end', width: '55%', height: 8, bgcolor: accent, borderRadius: radius }} />
      <Box sx={{ width: '76%', height: 3, bgcolor: ink, opacity: 0.6 }} />
      <Box sx={{ width: '52%', height: 3, bgcolor: ink, opacity: 0.3 }} />
    </Stack>
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ pl: 0.75, border: '1px solid',
      borderColor: `${ink}45`, borderRadius: radius, boxShadow: preset.id === 'pop' ? `2px 2px 0 ${ink}` : 'none' }}>
      <Box sx={{ width: '30%', height: 2, bgcolor: ink, opacity: 0.4 }} />
      <Box sx={{ height: 15, width: 19, display: 'grid', placeItems: 'center', bgcolor: accent, color: surface, borderRadius: radius }}>
        <SendRounded sx={{ fontSize: 10 }} />
      </Box>
    </Stack>
  </Box>
}

export function EngageStylePresetPicker({ value, onChange }: {
  value: WorkspaceStylePresetId; onChange: (value: WorkspaceStylePresetId) => void
}) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const selected = resolveWorkspaceStylePreset(value)
  return <>
    <Stack spacing={1}>
      <Typography variant="body2" fontWeight={600}>Workspace style</Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
        <Box sx={{ width: 112, flexShrink: 0 }}><StyleThumbnail preset={selected} /></Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="subtitle2" data-selected-style={selected.id}>{selected.label}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflowWrap: 'anywhere' }}>{selected.description}</Typography>
        </Box>
        <Tooltip title="Choose workspace style"><IconButton aria-label="Choose workspace style" aria-haspopup="dialog" onClick={() => setOpen(true)}><PaletteOutlined /></IconButton></Tooltip>
      </Box>
    </Stack>
    <Dialog open={open} onClose={() => setOpen(false)} maxWidth="md" fullWidth aria-labelledby={`${id}-title`}
      PaperProps={{ sx: { borderRadius: 1, maxHeight: 'calc(100dvh - 32px)', m: 2, width: 'calc(100% - 32px)' } }}>
      <DialogTitle id={`${id}-title`} sx={{ pr: 6 }}>Choose workspace style</DialogTitle>
      <Tooltip title="Close style gallery"><IconButton aria-label="Close style gallery" onClick={() => setOpen(false)} sx={{ position: 'absolute', top: 12, right: 12 }}><CloseRounded /></IconButton></Tooltip>
      <DialogContent dividers>
        <RadioGroup aria-label="Workspace style presets" value={selected.id} onChange={(_, next) => onChange(resolveWorkspaceStylePreset(next).id)}
          sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 1.5 }}>
          {WORKSPACE_STYLE_PRESETS.map(preset => {
            const active = preset.id === selected.id
            return <Box key={preset.id} component="label" sx={{ display: 'flex', flexDirection: 'column', cursor: 'pointer', minWidth: 0,
              p: 1.25, borderRadius: 1, border: '2px solid', borderColor: active ? 'primary.main' : 'divider', bgcolor: active ? 'action.selected' : 'background.paper',
              transition: 'border-color 140ms ease, background-color 140ms ease', '&:hover': { borderColor: 'primary.main' },
              '&:focus-within': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 2 },
              '@media (prefers-reduced-motion: reduce)': { transition: 'none' } }}>
              <StyleThumbnail preset={preset} />
              <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 0.75 }}>
                <Typography variant="subtitle2" id={`${id}-${preset.id}-label`}>{preset.label}</Typography>
                <Radio value={preset.id} size="small" autoFocus={active} sx={{ p: 0.5 }}
                  inputProps={{ 'aria-labelledby': `${id}-${preset.id}-label`, 'aria-describedby': `${id}-${preset.id}-description` }} />
              </Stack>
              <Typography variant="caption" color="text.secondary" id={`${id}-${preset.id}-description`} sx={{ overflowWrap: 'anywhere' }}>{preset.description}</Typography>
            </Box>
          })}
        </RadioGroup>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}><Button variant="contained" onClick={() => setOpen(false)}>Done</Button></DialogActions>
    </Dialog>
  </>
}
