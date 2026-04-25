import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { approveMerchantAccess } from '../api/merchant'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime } from '../utils/format'

const approvalSchema = z.object({
  approverName: z.string().min(2, 'Name is required.'),
  approverEmail: z.string().email('Enter a valid email.').optional().or(z.literal('')),
  approvedScope: z.string().default('IMPLEMENTATION_SUPPORT'),
})

type ApprovalForm = z.infer<typeof approvalSchema>

export function MerchantApprovalPage() {
  const { approvalCode = '' } = useParams()
  const { api } = useSupabaseAuth()
  const form = useForm<ApprovalForm>({
    resolver: zodResolver(approvalSchema),
    defaultValues: { approverName: '', approverEmail: '', approvedScope: 'IMPLEMENTATION_SUPPORT' },
  })
  const mutation = useMutation({
    mutationFn: (values: ApprovalForm) =>
      approveMerchantAccess(api, approvalCode, {
        approverName: values.approverName,
        approverEmail: values.approverEmail || undefined,
        approvedScope: values.approvedScope,
      }),
  })

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
      <Paper sx={{ width: '100%', maxWidth: 520, p: 3 }}>
        {mutation.data ? (
          <Stack spacing={2}>
            <StatusChip status={mutation.data.status} />
            <Typography variant="h1">Partner access approved</Typography>
            <Typography color="text.secondary">
              The partner can now see the scoped implementation summary for {mutation.data.shopDomain}.
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Approved at {formatDateTime(mutation.data.approvedAt)}
            </Typography>
          </Stack>
        ) : (
          <Stack component="form" spacing={2} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
            <Typography variant="h1">Approve scoped partner access</Typography>
            <Typography color="text.secondary">
              Approval gives the implementation partner store-level product setup and support visibility only.
            </Typography>
            {mutation.isError ? (
              <Alert severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Approval failed.'}</Alert>
            ) : null}
            <TextField label="Approver name" {...form.register('approverName')} error={Boolean(form.formState.errors.approverName)} helperText={form.formState.errors.approverName?.message} />
            <TextField label="Approver email" type="email" {...form.register('approverEmail')} error={Boolean(form.formState.errors.approverEmail)} helperText={form.formState.errors.approverEmail?.message} />
            <TextField label="Approved scope" {...form.register('approvedScope')} />
            <Button type="submit" variant="contained" disabled={mutation.isPending}>
              Approve partner access
            </Button>
          </Stack>
        )}
      </Paper>
    </Box>
  )
}
