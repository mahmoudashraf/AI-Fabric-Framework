import { useEffect, useState } from 'react'
import { Alert, Box, Button, Checkbox, CircularProgress, Dialog, DialogContent, DialogTitle, FormControlLabel,
  IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import { type CreateSupportCase } from '../../api/supportApi'
import { SupportResourcePicker } from './SupportResourcePicker'
import { useCustomerSupport } from './CustomerSupportContext'

const emptyRequest = (): CreateSupportCase => ({ summary: '', details: '', topic: '', resources: [], diagnosticConsent: false })
export function SupportRequestForm() {
  const support = useCustomerSupport()
  const [draft, setDraft] = useState(emptyRequest)
  useEffect(() => {
    if (support.requestOpen && support.pendingBody) setDraft(structuredClone(support.pendingBody))
  }, [support.requestOpen, support.pendingBody])
  const locked = support.saving || support.pendingIntake
  const submit = async () => {
    if (await support.submitIntake(draft, true)) setDraft(emptyRequest())
  }
  return <Dialog open={support.requestOpen} onClose={() => { if (!support.saving) support.setRequestOpen(false) }} maxWidth="sm" fullWidth>
    <DialogTitle component="div" sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: 1, borderColor: 'divider', py: 2 }}>
      <Box><Typography variant="overline" color="secondary.main">Let's get you connected</Typography>
        <Typography component="h2" variant="h6">Request integration help</Typography></Box>
      <IconButton aria-label="Close support request" disabled={support.saving} onClick={() => support.setRequestOpen(false)}><CloseRounded /></IconButton>
    </DialogTitle>
    <DialogContent sx={{ pt: '24px !important' }}>
      <Box component="form" onSubmit={event => { event.preventDefault(); void submit() }}>
        <Stack spacing={2.5}>
          <Typography variant="body2" color="text.secondary">Tell us what you want to connect. Keep passwords, tokens and private customer records out of this request.</Typography>
          {support.savedCase && <Alert severity="success">Request saved: {support.savedCase.reference}. Starting its conversation...</Alert>}
          {support.intakeError && <Alert severity="error">{support.intakeError} {support.pendingIntake
            ? 'Your submission is retained; retry sends the same request.'
            : 'Your entries are retained. Review them and submit again.'}</Alert>}
          <TextField label="Summary" required value={draft.summary} disabled={locked} inputProps={{ maxLength: 240 }}
            onChange={event => setDraft(value => ({ ...value, summary: event.target.value }))} />
          <TextField label="Details (optional)" multiline minRows={4} value={draft.details} disabled={locked} inputProps={{ maxLength: 12000 }}
            onChange={event => setDraft(value => ({ ...value, details: event.target.value }))} />
          <TextField select label="Product (optional)" value={draft.topic} disabled={locked} onChange={event => setDraft(value => ({ ...value, topic: event.target.value }))}>
            <MenuItem value="">Not sure yet</MenuItem>{['Platform', 'Engage', 'Desk', 'Knowledge and data'].map(topic => <MenuItem key={topic} value={topic}>{topic}</MenuItem>)}
          </TextField>
          <SupportResourcePicker customerId={support.customerId} value={draft.resources} disabled={locked} onChange={resources => setDraft(value => ({ ...value, resources }))} />
          <SupportConsent checked={draft.diagnosticConsent} disabled={locked} onChange={diagnosticConsent => setDraft(value => ({ ...value, diagnosticConsent }))} />
          <Button type="submit" variant="contained" size="large" startIcon={support.saving ? <CircularProgress size={16} color="inherit" /> : <ArrowForwardRounded />}
            disabled={support.saving || !draft.summary.trim()}>{support.saving ? (support.savedCase ? 'Starting conversation' : 'Saving request') : support.pendingIntake ? 'Retry same submission' : 'Submit request'}</Button>
        </Stack>
      </Box>
    </DialogContent>
  </Dialog>
}

export function SupportConsent({ checked, disabled, onChange }: { checked: boolean; disabled: boolean; onChange(value: boolean): void }) {
  return <Stack spacing={0.75}>
    <Stack direction="row" spacing={0.75} alignItems="center"><ShieldOutlined fontSize="small" color="secondary" /><Typography variant="subtitle2">Status-check permission</Typography></Stack>
    <FormControlLabel sx={{ alignItems: 'flex-start', m: 0 }} control={<Checkbox size="small" checked={checked} disabled={disabled} onChange={(_, value) => onChange(value)} />}
      label={<Typography variant="body2" sx={{ pt: 0.85 }}>Allow AI and the integration team to check these selected resources for this request</Typography>} />
    <Typography variant="caption" color="text.secondary">Release and verification state, app readiness, and connection/indexing status only. No credentials, raw logs or business records. Documentation help remains available without permission.</Typography>
  </Stack>
}
