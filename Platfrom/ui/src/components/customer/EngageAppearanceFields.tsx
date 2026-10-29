import { Alert, Button, MenuItem, Stack, TextField, Typography } from '@mui/material'
import RestartAltRounded from '@mui/icons-material/RestartAltRounded'
import type { WorkspaceAppearance } from '../../pages/aiWorkspaceAppearance'

export function EngageAppearanceFields({ value, onChange, onReset, error }: {
  value: WorkspaceAppearance; onChange: (value: WorkspaceAppearance) => void; onReset: () => void; error: string | null
}) {
  const set = (field: keyof WorkspaceAppearance, next: string) => onChange({ ...value, [field]: next })
  return <Stack spacing={2}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
      <Typography variant="subtitle2">Customer-facing appearance</Typography>
      <Button size="small" startIcon={<RestartAltRounded />} onClick={onReset}>Reset changes</Button>
    </Stack>
    <TextField label="Assistant name" value={value.assistantLabel} onChange={event => set('assistantLabel', event.target.value)} inputProps={{ maxLength: 128 }} />
    <TextField label="Logo image URL" type="url" value={value.logoUrl} placeholder="https://example.com/logo.png"
      helperText="Public HTTPS image. Clear to use the default assistant icon." onChange={event => set('logoUrl', event.target.value)} />
    <Stack direction="row" spacing={2}>
      <TextField label="Brand colour" type="color" value={value.primaryColor} onChange={event => set('primaryColor', event.target.value)} InputLabelProps={{ shrink: true }} sx={{ flex: 1, minWidth: 0 }} />
      <TextField select label="Appearance" value={value.darkMode} onChange={event => set('darkMode', event.target.value)} sx={{ flex: 1, minWidth: 0 }}>
        <MenuItem value="light">Light</MenuItem><MenuItem value="dark">Dark</MenuItem><MenuItem value="auto">System</MenuItem>
      </TextField>
    </Stack>
    <TextField label="Welcome message" multiline minRows={2} value={value.welcomeMessage} inputProps={{ maxLength: 1000 }}
      placeholder="Use the experience's default welcome" onChange={event => set('welcomeMessage', event.target.value)} />
    <TextField label="Input placeholder" value={value.placeholder} inputProps={{ maxLength: 1000 }} onChange={event => set('placeholder', event.target.value)} />
    {error ? <Alert severity="warning">{error}</Alert> : null}
  </Stack>
}
