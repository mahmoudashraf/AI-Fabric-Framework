import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import EditOutlined from '@mui/icons-material/EditOutlined'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import type { ChangePreview } from './brainChangePreview'

export function BrainChangeComparison({ preview, currentLabel, proposedLabel, href, canEdit }: {
  preview: ChangePreview; currentLabel: string; proposedLabel: string; href: (section: string) => string; canEdit: boolean
}) {
  return <Stack spacing={2.5} data-change-preview>
    <Typography variant="body2" color="text.secondary">{currentLabel} → {proposedLabel}</Typography>
    <Alert severity={preview.baseline === 'unavailable' ? 'warning' : 'info'}>
      {preview.baseline === 'unavailable' ? 'Applied configuration is unavailable. Only saved settings are shown; no comparison can be confirmed.'
        : preview.baseline === 'new' ? 'New setup. No version has been applied to this environment.' : `Not live yet · ${currentLabel} is unchanged.`}
    </Alert>
    {preview.values.map(value => <Box component="section" key={value.id}>
      <Typography component="h3" fontSize={17} fontWeight={650} sx={{ mb: 1 }}>{value.label}</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'minmax(0, 1fr) 20px minmax(0, 1fr)' }, gap: 1.25, alignItems: 'stretch' }}>
        {(['current', 'proposed'] as const).map((side, index) => <Box key={side} sx={{ display: 'contents' }}>
          {index === 1 && <Box sx={{ display: { xs: 'none', sm: 'grid' }, placeItems: 'center' }}><ArrowForwardRounded sx={{ fontSize: 19, color: 'text.secondary' }} /></Box>}
          <Box sx={{ p: 1.5, border: 1, borderRadius: 1, borderColor: side === 'proposed' ? 'success.main' : 'divider', bgcolor: side === 'proposed' ? 'action.selected' : 'action.hover', minWidth: 0 }}>
            <Typography variant="overline" sx={{ fontSize: 10, lineHeight: 1.5 }}>{side === 'current' ? 'Current' : 'Proposed'}</Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>{side === 'current' ? currentLabel : proposedLabel}</Typography>
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: 210, overflow: 'auto' }}>{value[side]}</Typography>
          </Box>
        </Box>)}
      </Box>
    </Box>)}
    {!preview.values.length && <Typography variant="body2" color="text.secondary">
      {preview.baseline === 'unavailable' ? 'A readable preview is not available for these saved settings.'
        : preview.fieldChanges.length ? 'Additional settings changed. Their field names are available in Technical details; stored values are not projected here.' : 'No field changes in this area.'}
    </Typography>}
    <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 2 }}>
      <Typography component="h3" fontSize={17} fontWeight={650} sx={{ mb: 0.75 }}>What this changes</Typography>
      <Typography variant="body2">{preview.impact}</Typography>
      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>Configuration preview, not a simulated AI answer. Changes take effect only after applying a version.</Typography>
      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>Verified saved prompt inputs are used where available; other current values come from the applied runtime. Publishing may add generated guidance to the saved draft.</Typography>
    </Box>
    <Accordion disableGutters elevation={0} sx={{ bgcolor: 'transparent', '&:before': { display: 'none' }, borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
      <AccordionSummary expandIcon={<ExpandMoreRounded />}><Typography variant="body2">Technical details</Typography></AccordionSummary>
      <AccordionDetails>
        <Typography variant="caption" color="text.secondary">Changed field names only. Unrecognised values, credentials and connection details are not shown.</Typography>
        {preview.baseline === 'unavailable' ? <Typography variant="body2">Field comparison unavailable.</Typography> : <Table size="small" aria-label="Changed configuration fields"><TableHead><TableRow><TableCell>Field</TableCell><TableCell>Change</TableCell></TableRow></TableHead>
          <TableBody>{preview.fieldChanges.map(item => <TableRow key={item.path}><TableCell sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>{item.path}</TableCell><TableCell>{item.change}</TableCell></TableRow>)}</TableBody>
        </Table>}
      </AccordionDetails>
    </Accordion>
    {preview.editor ? <Button component={RouterLink} to={href(preview.editor)} startIcon={<EditOutlined />} sx={{ alignSelf: 'flex-start' }}>{canEdit ? 'Edit' : 'View'} {preview.editorLabel}</Button>
      : <Typography variant="caption" color="text.secondary">This area has no dedicated editor in this workspace. Your integration team can help change these settings.</Typography>}
  </Stack>
}
