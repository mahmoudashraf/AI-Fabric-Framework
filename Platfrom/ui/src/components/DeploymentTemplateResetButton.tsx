import { useEffect, useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack } from '@mui/material'
import RestartAltOutlined from '@mui/icons-material/RestartAltOutlined'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { resetDeploymentToTemplate } from '../api/platformApi'

export function DeploymentTemplateResetButton({ deploymentId, disabled = false, onPendingChange }: {
  deploymentId: string; disabled?: boolean; onPendingChange?: (pending: boolean) => void
}) {
  // A different deployment must never inherit an open confirmation or a reset result.
  return <ScopedTemplateReset key={deploymentId} deploymentId={deploymentId} disabled={disabled} onPendingChange={onPendingChange} />
}

function ScopedTemplateReset({ deploymentId, disabled, onPendingChange }: {
  deploymentId: string; disabled: boolean; onPendingChange?: (pending: boolean) => void
}) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const reset = useMutation({
    retry: false,
    mutationFn: () => resetDeploymentToTemplate(deploymentId),
    onSuccess: async () => {
      setOpen(false)
      await Promise.all([
        ['deployment-draft', deploymentId], ['customer-storage-draft'], ['customer-assignment-draft'],
        ['deployment-validation'], ['deployment-overviews'], ['deployments'],
        ['deployment-workspace', deploymentId], ['deployment-config-diff-center', deploymentId],
        ['deployment-service-config-model', deploymentId], ['customer-deployment-portfolio'],
      ].map(queryKey => queryClient.invalidateQueries({ queryKey })))
    },
  })
  useEffect(() => { onPendingChange?.(reset.isPending) }, [onPendingChange, reset.isPending])
  return <Stack spacing={1}>
    <Button variant="outlined" startIcon={<RestartAltOutlined />} disabled={disabled || reset.isPending}
      onClick={() => { reset.reset(); setOpen(true) }}>Reset to template</Button>
    {reset.isSuccess ? <Alert severity="success">Template settings restored to the draft. The live deployment and app are unchanged.</Alert> : null}
    <Dialog open={open} onClose={() => { if (!reset.isPending) setOpen(false) }} aria-labelledby="template-reset-title">
      <DialogTitle id="template-reset-title">Reset to template?</DialogTitle>
      <DialogContent>
        <DialogContentText>Replace the saved draft settings with the original template defaults? Custom draft settings will be replaced. Installed plugins, external bindings and credentials are unchanged. Nothing will be published or deployed. Your existing deployment and app address will stay the same.</DialogContentText>
        {reset.isError ? <Alert severity="error" sx={{ mt: 2 }}>{reset.error instanceof Error ? reset.error.message : 'Template reset failed.'}</Alert> : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)} disabled={reset.isPending}>Cancel</Button>
        <Button variant="contained" onClick={() => reset.mutate()} disabled={disabled || reset.isPending}>
          {reset.isPending ? 'Resetting draft...' : 'Reset draft settings'}
        </Button>
      </DialogActions>
    </Dialog>
  </Stack>
}
